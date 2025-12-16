package com.chandler.key.example.encrypt.util;

import javax.crypto.Cipher;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 *
 * @author 钱丁君-chandler 2025/12/15
 */
public class RSAUtils {

    // RSA 算法
    private static final String ALGORITHM = "RSA";

    // 填充方式
    public enum RSAPadding {
        PKCS1("RSA/ECB/PKCS1Padding"),
        OAEP_SHA1("RSA/ECB/OAEPWithSHA-1AndMGF1Padding"),
        OAEP_SHA256("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");

        private final String transformation;

        RSAPadding(String transformation) {
            this.transformation = transformation;
        }

        public String getTransformation() {
            return transformation;
        }
    }

    // 签名算法
    public enum RSASignatureAlgorithm {
        SHA1("SHA1withRSA"),
        SHA256("SHA256withRSA"),
        SHA512("SHA512withRSA");

        private final String algorithm;

        RSASignatureAlgorithm(String algorithm) {
            this.algorithm = algorithm;
        }

        public String getAlgorithm() {
            return algorithm;
        }
    }

    /**
     * 生成 RSA 密钥对
     */
    public static KeyPair generateKeyPair(int keySize) throws Exception {
        if (keySize < 512) {
            throw new IllegalArgumentException("密钥长度至少为512位");
        }

        KeyPairGenerator keyPairGen = KeyPairGenerator.getInstance(ALGORITHM);
        keyPairGen.initialize(keySize, SecureRandom.getInstanceStrong());
        return keyPairGen.generateKeyPair();
    }

    /**
     * 加密（自动处理长度限制）
     */
    public static String encrypt(String data, String publicKeyStr, RSAPadding padding) throws Exception {
        PublicKey publicKey = getPublicKeyFromString(publicKeyStr);
        return encrypt(data, publicKey, padding);
    }

    public static String encrypt(String data, PublicKey publicKey, RSAPadding padding) throws Exception {
        Cipher cipher = Cipher.getInstance(padding.getTransformation());
        cipher.init(Cipher.ENCRYPT_MODE, publicKey);

        byte[] dataBytes = data.getBytes(StandardCharsets.UTF_8);
        int keySize = ((java.security.interfaces.RSAPublicKey) publicKey).getModulus().bitLength();
        int maxBlockSize = keySize / 8 - 11; // PKCS1 填充

        if (padding == RSAPadding.OAEP_SHA1 || padding == RSAPadding.OAEP_SHA256) {
            maxBlockSize = keySize / 8 - 42; // OAEP 填充
        }

        return processInBlocks(cipher, dataBytes, maxBlockSize, true);
    }

    /**
     * 解密
     */
    public static String decrypt(String encryptedData, String privateKeyStr, RSAPadding padding) throws Exception {
        PrivateKey privateKey = getPrivateKeyFromString(privateKeyStr);
        return decrypt(encryptedData, privateKey, padding);
    }

    public static String decrypt(String encryptedData, PrivateKey privateKey, RSAPadding padding) throws Exception {
        Cipher cipher = Cipher.getInstance(padding.getTransformation());
        cipher.init(Cipher.DECRYPT_MODE, privateKey);

        byte[] encryptedBytes = Base64.getDecoder().decode(encryptedData);
        int keySize = ((java.security.interfaces.RSAPrivateKey) privateKey).getModulus().bitLength();
        int maxBlockSize = keySize / 8;

        return processInBlocks(cipher, encryptedBytes, maxBlockSize, false);
    }

    /**
     * 分段处理（解决RSA长度限制问题）
     */
    private static String processInBlocks(Cipher cipher, byte[] data, int maxBlockSize, boolean isEncrypt) throws Exception {
        int offset = 0;
        ByteArrayOutputStream result = new ByteArrayOutputStream();

        while (offset < data.length) {
            int length = Math.min(maxBlockSize, data.length - offset);
            byte[] block = cipher.doFinal(data, offset, length);
            result.write(block, 0, block.length);
            offset += length;
        }

        if (isEncrypt) {
            return Base64.getEncoder().encodeToString(result.toByteArray());
        } else {
            return new String(result.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    /**
     * 生成数字签名
     */
    public static String sign(String data, String privateKeyStr, RSASignatureAlgorithm algorithm) throws Exception {
        PrivateKey privateKey = getPrivateKeyFromString(privateKeyStr);
        return sign(data, privateKey, algorithm);
    }

    public static String sign(String data, PrivateKey privateKey, RSASignatureAlgorithm algorithm) throws Exception {
        Signature signature = Signature.getInstance(algorithm.getAlgorithm());
        signature.initSign(privateKey);
        signature.update(data.getBytes(StandardCharsets.UTF_8));
        byte[] signBytes = signature.sign();
        return Base64.getEncoder().encodeToString(signBytes);
    }

    /**
     * 验证数字签名
     */
    public static boolean verify(String data, String sign, String publicKeyStr, RSASignatureAlgorithm algorithm) throws Exception {
        PublicKey publicKey = getPublicKeyFromString(publicKeyStr);
        return verify(data, sign, publicKey, algorithm);
    }

    public static boolean verify(String data, String sign, PublicKey publicKey, RSASignatureAlgorithm algorithm) throws Exception {
        Signature signature = Signature.getInstance(algorithm.getAlgorithm());
        signature.initVerify(publicKey);
        signature.update(data.getBytes(StandardCharsets.UTF_8));
        return signature.verify(Base64.getDecoder().decode(sign));
    }

    /**
     * 从字符串获取公钥
     */
    public static PublicKey getPublicKeyFromString(String publicKeyStr) throws Exception {
        byte[] keyBytes = Base64.getDecoder().decode(publicKeyStr);
        X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance(ALGORITHM);
        return keyFactory.generatePublic(spec);
    }

    /**
     * 从字符串获取私钥
     */
    public static PrivateKey getPrivateKeyFromString(String privateKeyStr) throws Exception {
        byte[] keyBytes = Base64.getDecoder().decode(privateKeyStr);
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance(ALGORITHM);
        return keyFactory.generatePrivate(spec);
    }

    /**
     * 获取公钥字符串
     */
    public static String getPublicKeyString(PublicKey publicKey) {
        return Base64.getEncoder().encodeToString(publicKey.getEncoded());
    }

    /**
     * 获取私钥字符串
     */
    public static String getPrivateKeyString(PrivateKey privateKey) {
        return Base64.getEncoder().encodeToString(privateKey.getEncoded());
    }

    /**
     * 测试方法
     */
    public static void main(String[] args) throws Exception {
        // 生成密钥对
        KeyPair keyPair = generateKeyPair(512);
        String publicKey = getPublicKeyString(keyPair.getPublic());
        String privateKey = getPrivateKeyString(keyPair.getPrivate());

        System.out.println("=== RSA 加密测试 ===");
        System.out.println("公钥: " + publicKey );
        System.out.println("私钥: " + privateKey );
        System.out.println("公钥长度: " + publicKey.length() + " 字符");
        System.out.println("私钥长度: " + privateKey.length() + " 字符");

        String originalData = "这是一段需要加密的测试数据，RSA适合加密小段数据！";
        originalData="wuKjRnoX2gyg+kApY1qrRw==";
        System.out.println("原始数据: " + originalData);

        // 测试不同填充模式
        for (RSAPadding padding : RSAPadding.values()) {
            try {
                System.out.println("\n=== 填充模式: " + padding + " ===");

                // 加密
                String encrypted = encrypt(originalData, publicKey, padding);
                System.out.println("加密结果: " + encrypted);

                // 解密
                String decrypted = decrypt(encrypted, privateKey, padding);
                System.out.println("解密结果: " + decrypted);
                System.out.println("验证: " + (originalData.equals(decrypted) ? "✓ 成功" : "✗ 失败"));

            } catch (Exception e) {
                System.out.println("错误: " + e.getMessage());
            }
        }

        // 测试数字签名
        System.out.println("\n=== 数字签名测试 ===");
        String dataToSign = "这是需要签名的数据";
        System.out.println("待签名数据: " + dataToSign);

        for (RSASignatureAlgorithm algorithm : RSASignatureAlgorithm.values()) {
            try {
                String signature = sign(dataToSign, privateKey, algorithm);
                System.out.println("\n签名算法: " + algorithm);
                System.out.println("签名: " + signature);

                boolean isValid = verify(dataToSign, signature, publicKey, algorithm);
                System.out.println("验证结果: " + (isValid ? "✓ 有效" : "✗ 无效"));

                // 测试篡改数据
                boolean isTamperedValid = verify(dataToSign + "tampered", signature, publicKey, algorithm);
                System.out.println("篡改后验证: " + (isTamperedValid ? "✗ 错误" : "✓ 正确"));

            } catch (Exception e) {
                System.out.println("签名失败: " + e.getMessage());
            }
        }
    }
}
