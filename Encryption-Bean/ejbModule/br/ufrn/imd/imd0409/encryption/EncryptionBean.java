package br.ufrn.imd.imd0409.encryption;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.KeySpec;
import java.util.concurrent.Future;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.PBEParameterSpec;
import javax.ejb.AsyncResult;
import javax.ejb.Asynchronous;
import javax.ejb.Local;
import javax.ejb.Remote;
import javax.ejb.SessionContext;
import javax.ejb.Stateless;

import org.apache.commons.codec.binary.Base64;

import br.ufrn.imd.imd0409.encryption.EncryptionException;
import br.ufrn.imd.imd0409.encryption.EncryptionLocalBusiness;
import br.ufrn.imd.imd0409.encryption.EncryptionRemoteBusiness;


@Stateless(name = EncryptionBean.EJB_NAME)
@Local(EncryptionLocalBusiness.class)
@Remote(EncryptionRemoteBusiness.class)
public class EncryptionBean implements EncryptionLocalBusiness,
		EncryptionRemoteBusiness {

	/**
	 * Name we'll assign to this EJB, will be referenced in the corresponding
	 * META-INF/ejb-jar.xml file
	 */
	static final String EJB_NAME = "EncryptionEJB";

	/**
	 * Name of the environment entry representing the ciphers' passphrase
	 * supplied in ejb-jar.xml
	 */
	private static final String ENV_ENTRY_NAME_CIPHERS_PASSPHRASE = "ciphersPassphrase";

	/**
	 * Name of the environment entry representing the message digest algorithm
	 * supplied in ejb-jar.xml
	 */
	private static final String ENV_ENTRY_NAME_MESSAGE_DIGEST_ALGORITHM = "messageDigestAlgorithm";

	/**
	 * Default Algorithm used by the Digest for one-way hashing
	 */
	private static final String DEFAULT_ALGORITHM_MESSAGE_DIGEST = "MD5";

	/**
	 * Charset used for encoding/decoding Strings to/from byte representation
	 */
	private static final String CHARSET = "UTF-8";

	/**
	 * Default Algorithm used by the Cipher Key for symmetric encryption
	 */
	private static final String DEFAULT_ALGORITHM_CIPHER = "PBEWithMD5AndDES";

	/**
	 * The default passphrase for symmetric encryption/decryption
	 */
	private static final String DEFAULT_PASSPHRASE = "LocalTestingPassphrase";

	/**
	 * The salt used in symmetric encryption/decryption
	 */
	private static final byte[] DEFAULT_SALT_CIPHERS = { (byte) 0xB4,
			(byte) 0xA2, (byte) 0x43, (byte) 0x89, 0x3E, (byte) 0xC5,
			(byte) 0x78, (byte) 0x53 };

	/**
	 * Iteration count used for symmetric encryption/decryption
	 */
	private static final int DEFAULT_ITERATION_COUNT_CIPHERS = 20;

	// ---------------------------------------------------------------------------||
	// Instance Members
	// ----------------------------------------------------------||
	// ---------------------------------------------------------------------------||

	/*
	 * The following members represent the internal state of the Service. Note
	 * how these are *not* leaked out via the end-user API, and are hence part
	 * of "internal state" and not "conversational state".
	 */

	/**
	 * SessionContext of this EJB; this will be injected by the EJB Container
	 * because it's marked w/ @Resource
	 */
	@Resource
	private SessionContext context;

	/**
	 * Passphrase to use for the key in cipher operations; lazily initialized
	 * and loaded via SessionContext.lookup
	 */
	private String ciphersPassphrase;

	/**
	 * Algorithm to use in message digest (hash) operations, injected via @Resource
	 * annotation with name property equal to env-entry name
	 */
	@Resource(name = ENV_ENTRY_NAME_MESSAGE_DIGEST_ALGORITHM)
	private String messageDigestAlgorithm;

	/**
	 * Digest used for one-way hashing
	 */
	private MessageDigest messageDigest;

	/**
	 * Cipher used for symmetric encryption
	 */
	private Cipher encryptionCipher;

	/**
	 * Cipher used for symmetric decryption
	 */
	private Cipher decryptionCipher;

	// ---------------------------------------------------------------------------||
	// Lifecycle
	// -----------------------------------------------------------------||
	// ---------------------------------------------------------------------------||

	/**
	 * Initializes this service before it may handle requests
	 * 
	 * @throws Exception
	 *             If some unexpected error occurred
	 */
	@PostConstruct
	public void initialize() throws Exception {
		// Log that we're here
		System.out.println("Initializing, part of " + PostConstruct.class.getName()
				+ " lifecycle");

		/*
		 * Symmetric Encryption
		 */

		// Obtain parameters used in initializing the ciphers
		final String cipherAlgorithm = DEFAULT_ALGORITHM_CIPHER;
		final byte[] ciphersSalt = DEFAULT_SALT_CIPHERS;
		final int ciphersIterationCount = DEFAULT_ITERATION_COUNT_CIPHERS;
		final String ciphersPassphrase = this.getCiphersPassphrase();

		// Obtain key and param spec for the ciphers
		final KeySpec ciphersKeySpec = new PBEKeySpec(
				ciphersPassphrase.toCharArray(), ciphersSalt,
				ciphersIterationCount);
		final SecretKey ciphersKey = SecretKeyFactory.getInstance(
				cipherAlgorithm).generateSecret(ciphersKeySpec);
		final AlgorithmParameterSpec paramSpec = new PBEParameterSpec(
				ciphersSalt, ciphersIterationCount);

		// Create and init the ciphers
		this.encryptionCipher = Cipher.getInstance(ciphersKey.getAlgorithm());
		this.decryptionCipher = Cipher.getInstance(ciphersKey.getAlgorithm());
		encryptionCipher.init(Cipher.ENCRYPT_MODE, ciphersKey, paramSpec);
		decryptionCipher.init(Cipher.DECRYPT_MODE, ciphersKey, paramSpec);

		// Log
		System.out.println("Initialized encryption cipher: " + this.encryptionCipher);
		System.out.println("Initialized decryption cipher: " + this.decryptionCipher);

		/*
		 * One-way Hashing
		 */

		// Get the algorithm for the MessageDigest
		final String messageDigestAlgorithm = this.getMessageDigestAlgorithm();

		// Create the MessageDigest
		try {
			this.messageDigest = MessageDigest
					.getInstance(messageDigestAlgorithm);
		} catch (NoSuchAlgorithmException e) {
			throw new RuntimeException("Could not obtain the "
					+ MessageDigest.class.getSimpleName() + " for algorithm: "
					+ messageDigestAlgorithm, e);
		}
		System.out.println("Initialized MessageDigest for one-way hashing: "
				+ this.messageDigest);
	}

	// ---------------------------------------------------------------------------||
	// Required Implementations
	// --------------------------------------------------||
	// ---------------------------------------------------------------------------||

	/**
	 * {@inheritDoc}
	 * 
	 * @see org.jboss.ejb3.examples.ch05.encryption.EncryptionCommonBusiness#compare(java.lang.String,
	 *      java.lang.String)
	 */
	@Override
	public boolean compare(final String hash, final String input)
			throws IllegalArgumentException, EncryptionException {
		// Precondition checks
		if (hash == null) {
			throw new IllegalArgumentException("hash is required.");
		}
		if (input == null) {
			throw new IllegalArgumentException("Input is required.");
		}

		// Get the hash of the supplied input
		final String hashOfInput = this.hash(input);

		// Determine whether equal
		final boolean equal = hash.equals(hashOfInput);

		// Return
		return equal;
	}

	/**
	 * {@inheritDoc}
	 * 
	 * @see org.jboss.ejb3.examples.ch05.encryption.EncryptionCommonBusiness#decrypt(java.lang.String)
	 */
	@Override
	public String decrypt(final String input) throws IllegalArgumentException,
			IllegalStateException, EncryptionException {
		// Get the cipher
		final Cipher cipher = this.decryptionCipher;
		if (cipher == null) {
			throw new IllegalStateException(
					"Decyrption cipher not available, has this service been initialized?");
		}

		// Run the cipher
		byte[] resultBytes = null;
		;
		try {
			final byte[] inputBytes = this.stringToByteArray(input);
			resultBytes = cipher.doFinal(Base64.decodeBase64(inputBytes));
		} catch (final Throwable t) {
			throw new EncryptionException("Error in decryption", t);
		}
		final String result = this.byteArrayToString(resultBytes);

		// Log
		System.out.println("Decryption on \"" + input + "\": " + result);

		// Return
		return result;
	}

	/**
	 * {@inheritDoc}
	 * 
	 * @see org.jboss.ejb3.examples.ch05.encryption.EncryptionCommonBusiness#encrypt(java.lang.String)
	 */
	@Override
	public String encrypt(final String input) throws IllegalArgumentException,
			EncryptionException {
		// Get the cipher
		final Cipher cipher = this.encryptionCipher;
		if (cipher == null) {
			throw new IllegalStateException(
					"Encyrption cipher not available, has this service been initialized?");
		}

		// Get bytes from the String
		byte[] inputBytes = this.stringToByteArray(input);

		// Run the cipher
		byte[] resultBytes = null;
		try {
			resultBytes = Base64.encodeBase64(cipher.doFinal(inputBytes));
		} catch (final Throwable t) {
			throw new EncryptionException("Error in encryption of: " + input, t);
		}

		// Log
		System.out.println("Encryption on \"" + input + "\": "
				+ this.byteArrayToString(resultBytes));

		// Return
		final String result = this.byteArrayToString(resultBytes);
		return result;
	}

	/**
	 * Note:
	 * 
	 * This is a weak implementation, but is enough to satisfy the example. If
	 * considering real-world stresses, we would be, at a minimum:
	 * 
	 * 1) Incorporating a random salt and storing it alongside the hashed result
	 * 2) Additionally implementing an iteration count to re-hash N times
	 */
	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.jboss.ejb3.examples.ch05.encryption.EncryptionCommonBusiness#hash
	 * (java.lang.String)
	 */
	@Override
	public String hash(final String input) throws IllegalArgumentException,
			EncryptionException {
		// Precondition check
		if (input == null) {
			throw new IllegalArgumentException("Input is required.");
		}

		// Get bytes from the input
		byte[] inputBytes = this.stringToByteArray(input);

		// Obtain the MessageDigest
		final MessageDigest digest = this.messageDigest;

		// Update with our input, and obtain the hash, resetting the
		// messageDigest
		digest.update(inputBytes, 0, inputBytes.length);
		final byte[] hashBytes = digest.digest();
		final byte[] encodedBytes = Base64.encodeBase64(hashBytes);

		// Get the input back in some readable format
		final String hash = this.byteArrayToString(encodedBytes);
		System.out.println("One-way hash of \"" + input + "\": " + hash);

		// Return
		return hash;
	}

	/**
	 * {@inheritDoc}
	 * 
	 * @see org.jboss.ejb3.examples.ch05.encryption.EncryptionCommonBusiness#hashAsync(java.lang.String)
	 */
	@Asynchronous
	@Override
	public Future<String> hashAsync(final String input)
			throws IllegalArgumentException, EncryptionException {
		// Get the real hash
		final String hash = this.hash(input);

		// Wrap and return
		return new AsyncResult<String>(hash);
	}

	/**
	 * Override the way we get the ciphers' passphrase so that we may define it
	 * in a secure location on the server. Now our production systems will use a
	 * different key for encoding than our development servers, and we may limit
	 * the likelihood of a security breach while still allowing our programmer
	 * to use the default passphrase transparently during development.
	 * 
	 * If not provided as an env-entry, fall back upon the default.
	 * 
	 * Note that a real system won't expose this method in the public API, ever.
	 * We do here for testing and to illustrate the example.
	 * 
	 * @see org.jboss.ejb3.examples.ch05.encryption.EncryptionBeanBase#getCiphersPassphrase()
	 */
	@Override
	public String getCiphersPassphrase() {
		// Obtain current
		String passphrase = this.ciphersPassphrase;

		// If not set
		if (passphrase == null) {

			// Do a lookup via SessionContext
			passphrase = this
					.getEnvironmentEntryAsString(ENV_ENTRY_NAME_CIPHERS_PASSPHRASE);

			// See if provided
			if (passphrase == null) {

				// Log a warning
				System.out.println("No encryption passphrase has been supplied explicitly via "
						+ "an env-entry, falling back on the default...");

				// Set
				passphrase = DEFAULT_PASSPHRASE;
			}

			// Set the passphrase to be used so we don't have to do this lazy
			// init again
			this.ciphersPassphrase = passphrase;
		}

		// In a secure system, we don't log this. ;)
		System.out.println("Using encryption passphrase for ciphers keys: " + passphrase);

		// Return
		return passphrase;
	}

	/**
	 * Obtains the message digest algorithm as injected from the env-entry
	 * element defined in ejb-jar.xml. If not specified, fall back onto the
	 * default, logging a warn message
	 * 
	 * @see org.jboss.ejb3.examples.ch05.encryption.EncryptionRemoteBusiness#getMessageDigestAlgorithm()
	 */
	@Override
	public String getMessageDigestAlgorithm() {
		// First see if this has been injected/set
		if (this.messageDigestAlgorithm == null) {
			// Log a warning
			System.out.println("No message digest algorithm has been supplied explicitly via "
					+ "an env-entry, falling back on the default...");

			// Set
			this.messageDigestAlgorithm = DEFAULT_ALGORITHM_MESSAGE_DIGEST;
		}

		// Log
		System.out.println("Configured MessageDigest one-way hash algorithm is: "
				+ this.messageDigestAlgorithm);

		// Return
		return this.messageDigestAlgorithm;
	}

	// ---------------------------------------------------------------------------||
	// Internal Helper Methods
	// ---------------------------------------------------||
	// ---------------------------------------------------------------------------||

	/**
	 * Obtains the environment entry with the specified name, casting to a
	 * String, and returning the result. If the entry is not assignable to a
	 * String, an {@link IllegalStateException} will be raised. In the event
	 * that the specified environment entry cannot be found, a warning message
	 * will be logged and we'll return null.
	 * 
	 * @param envEntryName
	 * @return
	 * @throws IllegalStateException
	 */
	private String getEnvironmentEntryAsString(final String envEntryName)
			throws IllegalStateException {
		// See if we have a SessionContext
		final SessionContext context = this.context;
		if (context == null) {
			System.out.println("No SessionContext, bypassing request to obtain environment entry: "
					+ envEntryName);
			return null;
		}

		// Lookup in the Private JNDI ENC via the injected SessionContext
		Object lookupValue = null;
		try {
			lookupValue = context.lookup(envEntryName);
			System.out.println("Obtained environment entry \"" + envEntryName + "\": "
					+ lookupValue);
		} catch (final IllegalArgumentException iae) {
			// Not found defined within this EJB's Component Environment,
			// so return null and let the caller handle it
			System.out.println("Could not find environment entry with name: "
					+ envEntryName);
			return null;
		}

		// Cast
		String returnValue = null;
		try {
			returnValue = String.class.cast(lookupValue);
		} catch (final ClassCastException cce) {
			throw new IllegalStateException("The specified environment entry, "
					+ lookupValue + ", was not able to be represented as a "
					+ String.class.getName(), cce);
		}

		// Return
		return returnValue;
	}

	/**
	 * Returns a String representation of the specified byte array using the
	 * charset from {@link EncryptionBeanBase#getCharset()}. Wraps any
	 * {@link UnsupportedEncodingException} as a result of using an invalid
	 * charset in a {@link RuntimeException}.
	 * 
	 * @param bytes
	 * @return
	 * @throws RuntimeException
	 *             If the charset was invalid, or some otehr unknown error
	 *             occurred
	 * @throws IllegalArgumentException
	 *             If the byte array was not specified
	 */
	private String byteArrayToString(final byte[] bytes)
			throws RuntimeException, IllegalArgumentException {
		// Precondition check
		if (bytes == null) {
			throw new IllegalArgumentException("Byte array is required.");
		}

		// Represent as a String
		String result = null;
		final String charset = this.getCharset();
		try {
			result = new String(bytes, charset);
		} catch (final UnsupportedEncodingException e) {
			throw new RuntimeException("Specified charset is invalid: "
					+ charset, e);
		}

		// Return
		return result;
	}

	/**
	 * Returns a byte array representation of the specified String using the
	 * charset from {@link EncryptionBeanBase#getCharset()}. Wraps any
	 * {@link UnsupportedEncodingException} as a result of using an invalid
	 * charset in a {@link RuntimeException}.
	 * 
	 * @param input
	 * @return
	 * @throws RuntimeException
	 *             If the charset was invalid, or some otehr unknown error
	 *             occurred
	 * @throws IllegalArgumentException
	 *             If the input was not specified (null)
	 */
	private byte[] stringToByteArray(final String input)
			throws RuntimeException, IllegalArgumentException {
		// Precondition check
		if (input == null) {
			throw new IllegalArgumentException("Input is required.");
		}

		// Represent as a String
		byte[] result = null;
		final String charset = this.getCharset();
		try {
			result = input.getBytes(charset);
		} catch (final UnsupportedEncodingException e) {
			throw new RuntimeException("Specified charset is invalid: "
					+ charset, e);
		}

		// Return
		return result;
	}

	/**
	 * Obtains the charset used in encoding/decoding Strings to/from byte
	 * representation
	 * 
	 * @return The charset
	 */
	private String getCharset() {
		return CHARSET;
	}

}
