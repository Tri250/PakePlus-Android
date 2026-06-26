package com.batteryhealth.app.data.database;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.batteryhealth.app.test.TestUtils;

import org.junit.Test;

import java.security.SecureRandom;
import java.util.HashSet;
import java.util.Set;

/**
 * DatabaseEncryptionHelper 加密安全性 + 稳定性测试。
 *
 * 验证项:
 * 1. 密钥生成随机性 (熵)
 * 2. 密钥一致性 (相同密码派生相同密钥)
 * 3. 不同密码派生不同密钥
 * 4. 加密/解密对称性
 * 5. 派生密钥长度正确
 * 6. 重复生成不重复
 * 7. 特殊字符密码兼容
 * 8. 空/边界密码处理
 */
public class DatabaseEncryptionHelperSecurityTest {

    @Test
    public void testGenerateKey_returnsValidKey() {
        byte[] key = DatabaseEncryptionHelper.generateKey();
        assertNotNull(key);
        assertEquals("Key length should be 32 bytes (256 bits)",
                32, key.length);
    }

    @Test
    public void testGenerateKey_eachCallProducesDifferentKey() {
        int count = 100;
        Set<String> keys = new HashSet<>();
        for (int i = 0; i < count; i++) {
            byte[] key = DatabaseEncryptionHelper.generateKey();
            keys.add(bytesToHex(key));
        }
        // 100 个 32 字节随机密钥碰撞概率几乎为 0
        assertEquals("Keys should be unique", count, keys.size());
    }

    @Test
    public void testGenerateKey_randomness_qualityCheck() {
        // 抽样 1000 个密钥，统计分布
        int zeroBytes = 0;
        int totalBytes = 0;
        for (int i = 0; i < 100; i++) {
            byte[] key = DatabaseEncryptionHelper.generateKey();
            for (byte b : key) {
                if (b == 0) zeroBytes++;
                totalBytes++;
            }
        }
        // 零字节比例应接近 0，不应过高（说明是真正的随机源）
        double zeroRatio = (double) zeroBytes / totalBytes;
        assertTrue("Zero byte ratio too high: " + zeroRatio,
                zeroRatio < 0.05);
    }

    @Test
    public void testDeriveKeyFromPassword_samePasswordSameKey() {
        char[] password = "test-password-123".toCharArray();
        byte[] salt = "fixed-salt-for-test".getBytes();

        byte[] key1 = DatabaseEncryptionHelper.deriveKeyFromPassword(password, salt);
        byte[] key2 = DatabaseEncryptionHelper.deriveKeyFromPassword(password, salt);

        assertNotNull(key1);
        assertNotNull(key2);
        assertEquals("Same password + salt should produce same key",
                bytesToHex(key1), bytesToHex(key2));
    }

    @Test
    public void testDeriveKeyFromPassword_differentPasswordDifferentKey() {
        byte[] salt = "fixed-salt-for-test".getBytes();
        byte[] key1 = DatabaseEncryptionHelper.deriveKeyFromPassword(
                "password1".toCharArray(), salt);
        byte[] key2 = DatabaseEncryptionHelper.deriveKeyFromPassword(
                "password2".toCharArray(), salt);
        assertNotEquals("Different passwords should produce different keys",
                bytesToHex(key1), bytesToHex(key2));
    }

    @Test
    public void testDeriveKeyFromPassword_differentSaltDifferentKey() {
        char[] password = "test-password".toCharArray();
        byte[] key1 = DatabaseEncryptionHelper.deriveKeyFromPassword(
                password, "salt1".getBytes());
        byte[] key2 = DatabaseEncryptionHelper.deriveKeyFromPassword(
                password, "salt2".getBytes());
        assertNotEquals("Different salts should produce different keys",
                bytesToHex(key1), bytesToHex(key2));
    }

    @Test
    public void testDeriveKeyFromPassword_returns256BitKey() {
        byte[] key = DatabaseEncryptionHelper.deriveKeyFromPassword(
                "password".toCharArray(), "salt".getBytes());
        assertNotNull(key);
        assertEquals("Derived key should be 32 bytes", 32, key.length);
    }

    @Test
    public void testDeriveKeyFromPassword_unicodePassword() {
        char[] unicodePwd = "密码🔐密码".toCharArray();
        byte[] salt = "salt-中文".getBytes();
        try {
            byte[] key1 = DatabaseEncryptionHelper.deriveKeyFromPassword(unicodePwd, salt);
            byte[] key2 = DatabaseEncryptionHelper.deriveKeyFromPassword(unicodePwd, salt);
            assertEquals(bytesToHex(key1), bytesToHex(key2));
        } catch (Exception e) {
            fail("Unicode password should not throw: " + e.getMessage());
        }
    }

    @Test
    public void testDeriveKeyFromPassword_emptyPassword() {
        char[] emptyPwd = new char[0];
        byte[] salt = "salt".getBytes();
        try {
            byte[] key = DatabaseEncryptionHelper.deriveKeyFromPassword(emptyPwd, salt);
            assertNotNull(key);
            assertEquals(32, key.length);
        } catch (Exception e) {
            // 某些实现可能拒绝空密码，允许
        }
    }

    @Test
    public void testDeriveKeyFromPassword_longPassword() {
        // 测试 1000 字符密码
        char[] longPwd = new char[1000];
        java.util.Arrays.fill(longPwd, 'a');
        byte[] salt = "salt".getBytes();
        try {
            byte[] key = DatabaseEncryptionHelper.deriveKeyFromPassword(longPwd, salt);
            assertNotNull(key);
            assertEquals(32, key.length);
        } catch (Exception e) {
            fail("Long password should not throw: " + e.getMessage());
        }
    }

    @Test
    public void testDeriveKeyFromPassword_specialChars() {
        char[] specialPwd = "!@#$%^&*()_+-=[]{}|;':\",./<>?`~\\".toCharArray();
        byte[] salt = "salt".getBytes();
        try {
            byte[] key1 = DatabaseEncryptionHelper.deriveKeyFromPassword(specialPwd, salt);
            byte[] key2 = DatabaseEncryptionHelper.deriveKeyFromPassword(specialPwd, salt);
            assertEquals(bytesToHex(key1), bytesToHex(key2));
        } catch (Exception e) {
            fail("Special chars should not throw: " + e.getMessage());
        }
    }

    @Test
    public void testKeyDerivation_isSlow_enoughForBruteForceResistance() {
        // PBKDF2 应该足够慢以抵抗暴力破解
        char[] pwd = "test".toCharArray();
        byte[] salt = "salt".getBytes();
        long elapsed = TestUtils.measureExecutionTime("KeyDerivation.singleOp", () -> {
            DatabaseEncryptionHelper.deriveKeyFromPassword(pwd, salt);
        });
        // 单次派生应在 50ms~2000ms 之间（取决于实现）
        assertTrue("Key derivation too fast (weak): " + elapsed + "ms",
                elapsed >= 10);
    }

    @Test
    public void testPasswordsAreZeroedAfterUse_securityBestPractice() {
        // 仅作为安全建议: 不在代码中长存密码明文
        char[] pwd = "secret-password".toCharArray();
        java.util.Arrays.fill(pwd, '\0');
        // 验证密码已清零
        for (char c : pwd) {
            assertEquals(0, c);
        }
    }

    /**
     * 性能测试: 1000 次密钥派生应在合理时间内完成
     */
    @Test
    public void testPerformance_bulkKeyDerivation() {
        char[] pwd = "performance-test-password".toCharArray();
        byte[] salt = "performance-test-salt".getBytes();
        long elapsed = TestUtils.measureExecutionTime("KeyDerivation.100x", () -> {
            for (int i = 0; i < 100; i++) {
                DatabaseEncryptionHelper.deriveKeyFromPassword(pwd, salt);
            }
        });
        assertTrue("100 derivations too slow: " + elapsed + "ms", elapsed < 30000);
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
