/**
 * 
 */
package br.ufrn.imd.imd0409.encryption;

import java.util.concurrent.Future;

/**
 * Contains the contract for operations common to all business interfaces of the
 * EncryptionEJB
 * 
 */
public interface EncryptionCommonBusiness {

	/**
	 * Encrypts the specified String, returning the result
	 * 
	 * @param input
	 * @return
	 * @throws IllegalArgumentException
	 *             If no input was provided (null)
	 * @throws EncryptionException
	 *             If some problem occurred with encryption
	 */
	String encrypt(String input) throws IllegalArgumentException,
			EncryptionException;

	/**
	 * Decrypts the specified String, returning the result. The general contract
	 * is that the result of decrypting a String encrypted with
	 * {@link EncryptionCommonBusiness#encrypt(String)} will be equal by value
	 * to the original input (round trip).
	 * 
	 * @param input
	 * @return
	 * @throws IllegalArgumentException
	 *             If no input was provided (null)
	 * @throws EncryptionException
	 *             If some problem occurred with decryption
	 */
	String decrypt(String input) throws IllegalArgumentException,
			EncryptionException;

	/**
	 * Returns a one-way hash of the specified argument. Useful for safely
	 * storing passwords.
	 * 
	 * @param input
	 * @return
	 * @throws IllegalArgumentException
	 *             If no input was provided (null)
	 * @throws EncryptionException
	 *             If some problem occurred making the hash
	 */
	String hash(String input) throws IllegalArgumentException,
			EncryptionException;

	/**
	 * Returns a one-way hash of the specified argument, calculated
	 * asynchronously. Useful for safely storing passwords.
	 * 
	 * @param input
	 * @return
	 * @throws IllegalArgumentException
	 * @throws EncryptionException
	 */
	Future<String> hashAsync(String input) throws IllegalArgumentException,
			EncryptionException;

	/**
	 * Returns whether or not the specified input matches the specified hash.
	 * Useful for validating passwords against a securely-stored hash.
	 * 
	 * @param hash
	 * @param input
	 * @return
	 * @throws IllegalArgumentException
	 *             If either the hash or input is not provided (null)
	 * @throws EncryptionException
	 *             If some problem occurred making the hash
	 */
	boolean compare(String hash, String input) throws IllegalArgumentException,
			EncryptionException;

	/*
	 * This comment applies to all below this marker.
	 * 
	 * In real life it's a security risk to expose these internals, but they're
	 * in place here for testing and to show functionality described by the
	 * examples.
	 */

	/**
	 * Obtains the passphrase to be used in the key for the symmetric
	 * encryption/decryption ciphers
	 * 
	 * @return
	 */
	String getCiphersPassphrase();

	/**
	 * Obtains the algorithm to be used in performing one-way hashing
	 * 
	 * @return
	 */
	String getMessageDigestAlgorithm();

}
