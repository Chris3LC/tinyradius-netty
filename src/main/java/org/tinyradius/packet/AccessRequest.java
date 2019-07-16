package org.tinyradius.packet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tinyradius.attribute.RadiusAttribute;
import org.tinyradius.dictionary.Dictionary;
import org.tinyradius.util.RadiusException;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.*;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;
import static org.tinyradius.attribute.Attributes.createAttribute;
import static org.tinyradius.packet.PacketType.ACCESS_REQUEST;
import static org.tinyradius.packet.Util.*;

/**
 * This class represents an Access-Request Radius packet.
 */
public class AccessRequest extends RadiusPacket {

    private static final Logger logger = LoggerFactory.getLogger(AccessRequest.class);

    public static final String AUTH_PAP = "pap";
    public static final String AUTH_CHAP = "chap";
    public static final String AUTH_MS_CHAP_V2 = "mschapv2";
    public static final String AUTH_EAP = "eap";

    public static final Set<String> AUTH_PROTOCOLS = new HashSet<>(Arrays.asList(AUTH_PAP, AUTH_CHAP, AUTH_MS_CHAP_V2, AUTH_EAP));

    // Temporary storage for the unencrypted User-Password attribute.
    private String password;

    private String authProtocol = AUTH_PAP;

    private byte[] chapPassword;
    private byte[] chapChallenge;

    private static final SecureRandom random = new SecureRandom();

    // Attributes
    private static final int USER_NAME = 1;
    private static final int USER_PASSWORD = 2;
    private static final int CHAP_PASSWORD = 3;
    private static final int CHAP_CHALLENGE = 60;
    private static final int EAP_MESSAGE = 79;

    // VendorIds
    private static final int MICROSOFT = 311;

    // Vendor Specific Attributes
    private static final int MS_CHAP_CHALLENGE = 11;
    private static final int MS_CHAP2_RESPONSE = 25;

    /**
     * @param dictionary
     * @param identifier
     * @param authenticator
     */
    public AccessRequest(Dictionary dictionary, int identifier, byte[] authenticator) {
        this(dictionary, identifier, authenticator, new ArrayList<>());
    }

    /**
     * @param dictionary
     * @param identifier
     * @param authenticator
     * @param attributes
     */
    public AccessRequest(Dictionary dictionary, int identifier, byte[] authenticator, List<RadiusAttribute> attributes) {
        super(dictionary, ACCESS_REQUEST, identifier, authenticator, attributes);
    }

    /**
     * Constructs an Access-Request packet, sets the
     * code, identifier and adds an User-Name and an
     * User-Password attribute (PAP).
     *
     * @param userName     user name
     * @param userPassword user password
     */
    public AccessRequest(Dictionary dictionary, int identifier, byte[] authenticator, String userName, String userPassword) {
        this(dictionary, identifier, authenticator);
        setUserName(userName);
        setUserPassword(userPassword);
    }

    /**
     * Sets the User-Name attribute of this Access-Request.
     *
     * @param userName user name to set
     */
    public void setUserName(String userName) {
        requireNonNull(userName, "user name not set");
        if (userName.isEmpty())
            throw new IllegalArgumentException("empty user name not allowed");

        removeAttributes(USER_NAME);
        addAttribute(createAttribute(getDictionary(), -1, USER_NAME, userName));
    }

    /**
     * Sets the plain-text user password.
     *
     * @param userPassword user password to set
     */
    public void setUserPassword(String userPassword) {
        if (userPassword == null || userPassword.isEmpty())
            throw new IllegalArgumentException("password is empty");
        this.password = userPassword;
    }

    /**
     * Retrieves the plain-text user password.
     * Returns null for CHAP - use verifyPassword().
     *
     * @return user password in plaintext if decoded and using PAP
     * @see #verifyPassword(String)
     */
    public String getUserPassword() {
        return password;
    }

    /**
     * Retrieves the user name from the User-Name attribute.
     *
     * @return user name
     */
    public String getUserName() {
        final RadiusAttribute attribute = getAttribute(USER_NAME);
        return attribute == null ?
                null : attribute.getDataString();
    }

    /**
     * Returns the protocol used for encrypting the passphrase.
     *
     * @return one of {@link #AUTH_PROTOCOLS}
     */
    public String getAuthProtocol() {
        return authProtocol;
    }

    /**
     * Selects the protocol to use for encrypting the passphrase when
     * encoding this Radius packet.
     *
     * @param authProtocol {@link #AUTH_PROTOCOLS}
     */
    public void setAuthProtocol(String authProtocol) {
        if (authProtocol != null && AUTH_PROTOCOLS.contains(authProtocol))
            this.authProtocol = authProtocol;
        else
            throw new IllegalArgumentException("protocol must be in " + AUTH_PROTOCOLS);
    }


    /**
     * AccessRequest does not verify authenticator as they
     * contain random bytes.
     * <p>
     * Instead it checks the User-Password/Challenge attributes
     * are present and attempts decryption.
     *
     * @param sharedSecret shared secret, only applicable for PAP
     * @param ignored      ignored, not applicable for AccessRequest
     */
    @Override
    protected void decode(String sharedSecret, byte[] ignored) throws RadiusException {
        // detect auth protocol
        RadiusAttribute userPassword = getAttribute(USER_PASSWORD);
        RadiusAttribute chapPassword = getAttribute(CHAP_PASSWORD);
        RadiusAttribute chapChallenge = getAttribute(CHAP_CHALLENGE);
        RadiusAttribute msChapChallenge = getAttribute(MICROSOFT, MS_CHAP_CHALLENGE);
        RadiusAttribute msChap2Response = getAttribute(MICROSOFT, MS_CHAP2_RESPONSE);
        List<RadiusAttribute> eapMessage = getAttributes(EAP_MESSAGE);

        if (userPassword != null) {
            setAuthProtocol(AUTH_PAP);
            this.password = decodePapPassword(userPassword.getData(), sharedSecret.getBytes(UTF_8));
        } else if (chapPassword != null) {
            setAuthProtocol(AUTH_CHAP);
            this.chapPassword = chapPassword.getData();
            this.chapChallenge = chapChallenge != null ?
                    chapChallenge.getData() : getAuthenticator();
        } else if (msChapChallenge != null && msChap2Response != null) {
            setAuthProtocol(AUTH_MS_CHAP_V2);
            this.chapPassword = msChap2Response.getData();
            this.chapChallenge = msChapChallenge.getData();
        } else if (eapMessage.size() > 0) {
            setAuthProtocol(AUTH_EAP);
        } else
            throw new RadiusException("Access-Request: User-Password or CHAP-Password/CHAP-Challenge missing");
    }

    /**
     * Verifies that the passed plain-text password matches the password
     * (hash) send with this Access-Request packet. Works with both PAP
     * and CHAP.
     *
     * @param plaintext password to verify packet against
     * @return true if the password is valid, false otherwise
     */
    public boolean verifyPassword(String plaintext) throws UnsupportedOperationException {
        if (plaintext == null || plaintext.isEmpty())
            throw new IllegalArgumentException("password is empty");
        switch (getAuthProtocol()) {
            case AUTH_CHAP:
                return verifyChapPassword(plaintext);
            case AUTH_MS_CHAP_V2:
                throw new UnsupportedOperationException(AUTH_MS_CHAP_V2 + " verification not supported yet");
            case AUTH_EAP:
                throw new UnsupportedOperationException(AUTH_EAP + " verification not supported yet");
            case AUTH_PAP:
            default:
                return getUserPassword().equals(plaintext);
        }
    }

    /**
     * AccessRequest overrides this method to generate a randomized authenticator as per RFC 2865
     * and encode required attributes (i.e. User-Password).
     *
     * @param sharedSecret shared secret that secures the communication
     *                     with the other Radius server/client
     * @return RadiusPacket with new authenticator and/or encoded attributes
     * @throws UnsupportedOperationException auth type not supported
     */
    @Override
    public AccessRequest encodeRequest(String sharedSecret) throws UnsupportedOperationException {
        if (sharedSecret == null || sharedSecret.isEmpty())
            throw new IllegalArgumentException("shared secret cannot be null/empty");

        // create authenticator only if needed
        byte[] newAuthenticator = getAuthenticator() == null ? random16bytes() : getAuthenticator();

        final AccessRequest accessRequest = new AccessRequest(getDictionary(), getPacketIdentifier(), newAuthenticator, new ArrayList<>(getAttributes()));

        // encode attributes (User-Password attribute needs the new authenticator)
        encodeRequestAttributes(newAuthenticator, sharedSecret).forEach(a -> {
            accessRequest.removeAttributes(a.getType());
            accessRequest.addAttribute(a);
        });
        return accessRequest;
    }

    /**
     * Sets and encrypts the User-Password attribute.
     *
     * @param sharedSecret shared secret that secures the communication
     *                     with the other Radius server/client
     * @return List of RadiusAttributes to override
     * @throws UnsupportedOperationException auth protocol not supported
     */
    protected List<RadiusAttribute> encodeRequestAttributes(byte[] newAuthenticator, String sharedSecret) throws UnsupportedOperationException {
        if (password != null && !password.isEmpty())
            switch (getAuthProtocol()) {
                case AUTH_PAP:
                    return Collections.singletonList(
                            createAttribute(getDictionary(), -1, USER_PASSWORD,
                                    encodePapPassword(newAuthenticator, password.getBytes(UTF_8), sharedSecret.getBytes(UTF_8))));
                case AUTH_CHAP:
                    byte[] challenge = random16bytes();
                    return Arrays.asList(
                            createAttribute(getDictionary(), -1, CHAP_CHALLENGE, challenge),
                            createAttribute(getDictionary(), -1, CHAP_PASSWORD,
                                    computeChapPassword((byte) random.nextInt(256), password, challenge)));
                case AUTH_MS_CHAP_V2:
                    throw new UnsupportedOperationException("encoding not supported for " + AUTH_MS_CHAP_V2);
                case AUTH_EAP:
                    throw new UnsupportedOperationException("encoding not supported for " + AUTH_EAP);
            }

        return Collections.emptyList();
    }

    /**
     * This method encodes the plaintext user password according to RFC 2865.
     *
     * @param userPass     the password to encrypt
     * @param sharedSecret shared secret
     * @return the byte array containing the encrypted password
     */
    private byte[] encodePapPassword(byte[] newAuthenticator, byte[] userPass, byte[] sharedSecret) {
        requireNonNull(userPass, "userPass cannot be null");
        requireNonNull(sharedSecret, "sharedSecret cannot be null");

        byte[] C = newAuthenticator;
        byte[] P = pad(userPass, C.length);
        byte[] result = new byte[P.length];

        for (int i = 0; i < P.length; i += C.length) {
            C = compute(sharedSecret, C);
            C = xor(P, i, C.length, C, 0, C.length);
            System.arraycopy(C, 0, result, i, C.length);
        }

        return result;
    }

    /**
     * Decodes the passed encrypted password and returns the clear-text form.
     *
     * @param encryptedPass encrypted password
     * @param sharedSecret  shared secret
     * @return decrypted password
     */
    private String decodePapPassword(byte[] encryptedPass, byte[] sharedSecret) throws RadiusException {
        if (encryptedPass == null || encryptedPass.length < 16) {
            // PAP passwords require at least 16 bytes
            logger.warn("invalid Radius packet: User-Password attribute with malformed PAP password, length = "
                    + (encryptedPass == null ? 0 : encryptedPass.length) + ", but length must be greater than 15");
            throw new RadiusException("malformed User-Password attribute");
        }

        byte[] result = new byte[encryptedPass.length];
        byte[] C = this.getAuthenticator();

        for (int i = 0; i < encryptedPass.length; i += C.length) {
            C = compute(sharedSecret, C);
            C = xor(encryptedPass, i, C.length, C, 0, C.length);
            System.arraycopy(C, 0, result, i, C.length);
            System.arraycopy(encryptedPass, i, C, 0, C.length);
        }

        return getStringFromUtf8(result);
    }

    /**
     * Encodes a plain-text password using the given CHAP challenge.
     * See RFC 2865 section 2.2
     *
     * @param chapId        CHAP ID associated with request
     * @param plaintextPw   plain-text password
     * @param chapChallenge random 16 octet CHAP challenge
     * @return 17 octet CHAP-encoded password (1 octet for CHAP ID, 16 octets CHAP response)
     */
    private byte[] computeChapPassword(byte chapId, String plaintextPw, byte[] chapChallenge) {
        MessageDigest md5 = getMd5Digest();
        md5.update(chapId);
        md5.update(plaintextPw.getBytes(UTF_8));
        md5.update(chapChallenge);

        return ByteBuffer.allocate(17)
                .put(chapId)
                .put(md5.digest())
                .array();
    }

    /**
     * Verifies a CHAP password against the given plaintext password.
     *
     * @param plaintext
     * @return plain-text password
     */
    private boolean verifyChapPassword(String plaintext) {
        if (plaintext == null || plaintext.isEmpty())
            logger.warn("plaintext must not be empty");
        else if (chapChallenge == null)
            logger.warn("CHAP challenge is null");
        else if (chapPassword == null || chapPassword.length != 17)
            logger.warn("CHAP password must be 17 bytes");
        else
            return Arrays.equals(chapPassword, computeChapPassword(chapPassword[0], plaintext, chapChallenge));
        return false;
    }

    private byte[] compute(byte[]... values) {
        MessageDigest md = getMd5Digest();

        for (byte[] b : values)
            md.update(b);

        return md.digest();
    }

    private byte[] random16bytes() {
        byte[] randomBytes = new byte[16];
        random.nextBytes(randomBytes);
        return randomBytes;
    }
}
