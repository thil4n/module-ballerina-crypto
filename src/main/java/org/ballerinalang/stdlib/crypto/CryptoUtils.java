/*
 * Copyright (c) 2018, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.ballerinalang.stdlib.crypto;

import org.ballerinalang.bre.Context;
import org.ballerinalang.bre.bvm.BLangVMErrors;
import org.ballerinalang.connector.api.BLangConnectorSPIUtil;
import org.ballerinalang.model.types.BTypes;
import org.ballerinalang.model.values.BError;
import org.ballerinalang.model.values.BMap;
import org.ballerinalang.model.values.BString;
import org.ballerinalang.model.values.BValue;
import org.ballerinalang.model.values.BValueArray;
import org.ballerinalang.util.exceptions.BallerinaException;

import java.io.UnsupportedEncodingException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.spec.AlgorithmParameterSpec;
import java.util.ArrayList;
import java.util.List;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.Mac;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Utility functions relevant to crypto operations.
 *
 * @since 0.95.1
 */
public class CryptoUtils {

    private CryptoUtils() {

    }

    /**
     * Generate HMAC of a byte array based on the provided HMAC algorithm.
     *
     * @param context   BRE context used to raise error messages
     * @param algorithm algorithm used during HMAC generation
     * @param key       key used during HMAC generation
     * @param input     input byte array for HMAC generation
     * @return calculated HMAC value
     */
    public static byte[] hmac(Context context, String algorithm, byte[] key, byte[] input) {
        try {
            SecretKey secretKey = new SecretKeySpec(key, algorithm);
            Mac mac = Mac.getInstance(algorithm);
            mac.init(secretKey);
            return mac.doFinal(input);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new BallerinaException("error occurred while calculating HMAC: " + e.getMessage(), context);
        }
    }

    /**
     * Generate Hash of a byte array based on the provided hashing algorithm.
     *
     * @param context   BRE context used to raise error messages
     * @param algorithm algorithm used during hashing
     * @param input     input byte array for hashing
     * @return calculated hash value
     */
    public static byte[] hash(Context context, String algorithm, byte[] input) {
        try {
            MessageDigest messageDigest;
            messageDigest = MessageDigest.getInstance(algorithm);
            messageDigest.update(input);
            return messageDigest.digest();
        } catch (NoSuchAlgorithmException e) {
            throw new BallerinaException("error occurred while calculating hash: " + e.getMessage(), context);
        }
    }


    /**
     * Generate signature of a byte array based on the provided signing algorithm.
     *
     * @param context    BRE context used to raise error messages
     * @param algorithm  algorithm used during signing
     * @param privateKey private key to be used during signing
     * @param input      input byte array for signing
     * @return calculated signature
     * @throws InvalidKeyException if the privateKey is invalid
     */
    public static byte[] sign(Context context, String algorithm, PrivateKey privateKey, byte[] input)
            throws InvalidKeyException {
        try {
            Signature sig = Signature.getInstance(algorithm);
            sig.initSign(privateKey);
            sig.update(input);
            return sig.sign();
        } catch (NoSuchAlgorithmException | SignatureException e) {
            throw new BallerinaException("error occurred while calculating signature: " + e.getMessage(), context);
        }
    }

    /**
     * Create crypto error.
     *
     * @param context Represent ballerina context
     * @param errMsg  Error description
     * @return conversion error
     */
    public static BError createCryptoError(Context context, String errMsg) {
        BMap<String, BValue> errorRecord = BLangConnectorSPIUtil.createBStruct(context, Constants.CRYPTO_PACKAGE,
                Constants.CRYPTO_ERROR);
        errorRecord.put(Constants.MESSAGE, new BString(errMsg));
        return BLangVMErrors.createError(context, true, BTypes.typeError, Constants.ENCODING_ERROR_CODE, errorRecord);
    }

    /**
     * Encrypt or decrypt byte array based on RSA algorithm.
     *
     * @param context          BRE context used to raise error messages
     * @param cipherMode       cipher mode depending on encryption or decryption
     * @param algorithmMode    mode used during encryption
     * @param algorithmPadding padding used during encryption
     * @param key              key to be used during encryption
     * @param input            input byte array for encryption
     * @return encrypted value
     * @throws InvalidKeyException if the privateKey is invalid
     */
    public static byte[] rsaEncryptDecrypt(Context context, CipherMode cipherMode, String algorithmMode,
                                           String algorithmPadding, Key key, byte[] input, byte[] iv)
            throws InvalidKeyException {
        try {
            algorithmMode = transformAlgorithmMode(context, algorithmMode);
            algorithmPadding = transformAlgorithmPadding(context, algorithmPadding);
            if (algorithmPadding != null && algorithmMode != null) {
                Cipher cipher = Cipher.getInstance("RSA/" + algorithmMode + "/" + algorithmPadding);
                AlgorithmParameterSpec paramSpec = buildParameterSpec(context, algorithmMode, iv, 0);
                initCipher(cipher, cipherMode, key, paramSpec);
                return cipher.doFinal(input);
            } else {
                throw new BallerinaException("error", context);
            }
        } catch (NoSuchAlgorithmException e) {
            throw new BallerinaException("error occurred while calculating signature: " + e.getMessage(), context);
        } catch (NoSuchPaddingException e) {
            throw new BallerinaException("error", context);
        } catch (IllegalBlockSizeException e) {
            throw new BallerinaException("error", context);
        } catch (BadPaddingException e) {
            throw new BallerinaException("error", context);
        } catch (InvalidAlgorithmParameterException e) {
            throw new BallerinaException("error", context);
        }
    }

    private static final List<Integer> VALID_TAG_SIZES = new ArrayList<>();
    static {
        VALID_TAG_SIZES.add(32);
        VALID_TAG_SIZES.add(63);
        VALID_TAG_SIZES.add(96);
        VALID_TAG_SIZES.add(104);
        VALID_TAG_SIZES.add(112);
        VALID_TAG_SIZES.add(120);
        VALID_TAG_SIZES.add(128);
    }

    /**
     * Encrypt or decrypt byte array based on AES algorithm.
     *
     * @param context          BRE context used to raise error messages
     * @param cipherMode       cipher mode depending on encryption or decryption
     * @param algorithmMode    mode used during encryption
     * @param algorithmPadding padding used during encryption
     * @param key              key to be used during encryption
     * @param input            input byte array for encryption
     * @param iv               initialization vector
     */
    public static void aesEncryptDecrypt(Context context, CipherMode cipherMode, String algorithmMode,
                                           String algorithmPadding, byte[] key, byte[] input, byte[] iv, long tagSize) {
        try {
            String transformedAlgorithmMode = transformAlgorithmMode(context, algorithmMode);
            String transformedAlgorithmPadding = transformAlgorithmPadding(context, algorithmPadding);
            SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
            if (!VALID_TAG_SIZES.contains(tagSize)) {
                context.setReturnValues(CryptoUtils.createCryptoError(context, "valid tag sizes are: " +
                        VALID_TAG_SIZES.toString()));
            }
            AlgorithmParameterSpec paramSpec = buildParameterSpec(context, transformedAlgorithmMode, iv, (int) tagSize);
            Cipher cipher = Cipher.getInstance("AES/" + transformedAlgorithmMode + "/" + transformedAlgorithmPadding);
            initCipher(cipher, cipherMode, keySpec, paramSpec);
            context.setReturnValues(new BValueArray(cipher.doFinal(input)));
        } catch (NoSuchAlgorithmException e) {
            context.setReturnValues(CryptoUtils.createCryptoError(context, "unsupported algorithm: AES " +
                    algorithmMode + " " + algorithmPadding));
        } catch (InvalidKeyException e) {
            context.setReturnValues(CryptoUtils.createCryptoError(context, e.getMessage()));
        } catch (InvalidAlgorithmParameterException e) {
            context.setReturnValues(CryptoUtils.createCryptoError(context, e.getMessage()));
        } catch (NoSuchPaddingException e) {
            context.setReturnValues(CryptoUtils.createCryptoError(context, "unsupported padding scheme defined in " +
                    "the algorithm: AES " + algorithmMode + " " + algorithmPadding));
        } catch (BadPaddingException e) {
            context.setReturnValues(CryptoUtils.createCryptoError(context, e.getMessage()));
        } catch (IllegalBlockSizeException e) {
            context.setReturnValues(CryptoUtils.createCryptoError(context, e.getMessage()));
        } catch (BallerinaException e) {
            context.setReturnValues(CryptoUtils.createCryptoError(context, e.getMessage()));
        }
    }

    private static void initCipher(Cipher cipher, CipherMode cipherMode, Key key, AlgorithmParameterSpec ivSpec)
            throws InvalidKeyException, InvalidAlgorithmParameterException {
        if (cipherMode == CipherMode.ENCRYPT) {
            if (ivSpec == null) {
                cipher.init(Cipher.ENCRYPT_MODE, key);
            } else {
                cipher.init(Cipher.ENCRYPT_MODE, key, ivSpec);
            }
        } else {
            if (ivSpec == null) {
                cipher.init(Cipher.DECRYPT_MODE, key);
            } else {
                cipher.init(Cipher.DECRYPT_MODE, key, ivSpec);
            }
        }
    }

    private static AlgorithmParameterSpec buildParameterSpec(Context context, String algorithmMode, byte[] iv,
                                                             int tagSize) {
        if (algorithmMode.equals("GCM")) {
            if (iv == null) {
                iv = new byte[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
                return new GCMParameterSpec(tagSize, iv);
            } else {
                return new GCMParameterSpec(tagSize, iv);
            }
        } else if (algorithmMode.equals("CBC")) {
            if (iv == null) {
                iv = new byte[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
                return new IvParameterSpec(iv);
            } else {
                return new IvParameterSpec(iv);
            }
        } else if (algorithmMode.equals("ECB") && iv != null) {
            throw new BallerinaException("ECB mode cannot use IV", context);
        } else {
            return null;
        }
    }

    private static String transformAlgorithmMode(Context context, String algorithmMode) throws BallerinaException {
        if (!algorithmMode.equals("CBC") && !algorithmMode.equals("ECB") && !algorithmMode.equals("GCM")) {
            throw new BallerinaException("unsupported mode: " + algorithmMode, context);
        }
        return algorithmMode;
    }

    private static String transformAlgorithmPadding(Context context, String algorithmPadding)
            throws BallerinaException {
        if (algorithmPadding.equals("PKCS1")) {
            algorithmPadding = "PKCS1Padding";
        } else if (algorithmPadding.equals("PKCS5")) {
            algorithmPadding = "PKCS5Padding";
        } else if (algorithmPadding.equals("OAEP")) {
            algorithmPadding = "OAEPPadding";
        } else if (algorithmPadding.equals("NONE")) {
            algorithmPadding = "NoPadding";
        } else {
            throw new BallerinaException("unsupported padding: " + algorithmPadding, context);
        }
        return algorithmPadding;
    }

    public enum CipherMode {ENCRYPT, DECRYPT}
}
