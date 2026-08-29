package com.limelight.nvstream.http;

import org.bouncycastle.crypto.BlockCipher;
import org.bouncycastle.crypto.engines.AESLightEngine;
import org.bouncycastle.crypto.params.KeyParameter;
import org.xmlpull.v1.XmlPullParserException;

import com.limelight.LimeLog;

import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.ProtocolException;
import java.net.SocketException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.SignatureException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Locale;

public class PairingManager {

    private static final long INITIAL_PAIR_RETRY_WINDOW_MS = 120_000L;
    private static final long INITIAL_PAIR_RETRY_DELAY_MS = 200L;

    private NvHTTP http;

    private PrivateKey pk;
    private X509Certificate cert;
    private byte[] pemCertBytes;

    private X509Certificate serverCert;

    public enum PairState {
        NOT_PAIRED,
        PAIRED,
        PIN_WRONG,
        FAILED,
        ALREADY_IN_PROGRESS
    }

    public PairingManager(NvHTTP http, LimelightCryptoProvider cryptoProvider) {
        this.http = http;
        this.cert = cryptoProvider.getClientCertificate();
        this.pemCertBytes = cryptoProvider.getPemEncodedClientCertificate();
        this.pk = cryptoProvider.getClientPrivateKey();
    }

    final private static char[] hexArray = "0123456789ABCDEF".toCharArray();
    private static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for ( int j = 0; j < bytes.length; j++ ) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }

    private static byte[] hexToBytes(String s) {
        int len = s.length();
        if (len % 2 != 0) {
            throw new IllegalArgumentException("Illegal string length: "+len);
        }

        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                                 + Character.digit(s.charAt(i+1), 16));
        }
        return data;
    }

    private X509Certificate extractPlainCert(String text) throws XmlPullParserException, IOException
    {
        String certText = NvHTTP.getXmlString(text, "plaincert", false);
        if (certText != null) {
            byte[] certBytes = hexToBytes(certText);

            try {
                CertificateFactory cf = CertificateFactory.getInstance("X.509");
                return (X509Certificate)cf.generateCertificate(new ByteArrayInputStream(certBytes));
            } catch (CertificateException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        }
        else {
            return null;
        }
    }

    private byte[] generateRandomBytes(int length)
    {
        byte[] rand = new byte[length];
        new SecureRandom().nextBytes(rand);
        return rand;
    }

    private static byte[] saltPin(byte[] salt, String pin) throws UnsupportedEncodingException {
        byte[] saltedPin = new byte[salt.length + pin.length()];
        System.arraycopy(salt, 0, saltedPin, 0, salt.length);
        System.arraycopy(pin.getBytes("UTF-8"), 0, saltedPin, salt.length, pin.length());
        return saltedPin;
    }

    private static Signature getSha256SignatureInstanceForKey(Key key) throws NoSuchAlgorithmException {
        switch (key.getAlgorithm()) {
            case "RSA":
                return Signature.getInstance("SHA256withRSA");
            case "EC":
                return Signature.getInstance("SHA256withECDSA");
            default:
                throw new NoSuchAlgorithmException("Unhandled key algorithm: " + key.getAlgorithm());
        }
    }

    private static boolean verifySignature(byte[] data, byte[] signature, Certificate cert) {
        try {
            Signature sig = PairingManager.getSha256SignatureInstanceForKey(cert.getPublicKey());
            sig.initVerify(cert.getPublicKey());
            sig.update(data);
            return sig.verify(signature);
        } catch (NoSuchAlgorithmException | SignatureException | InvalidKeyException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    private static byte[] signData(byte[] data, PrivateKey key) {
        try {
            Signature sig = PairingManager.getSha256SignatureInstanceForKey(key);
            sig.initSign(key);
            sig.update(data);
            return sig.sign();
        } catch (NoSuchAlgorithmException | SignatureException | InvalidKeyException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    private static byte[] performBlockCipher(BlockCipher blockCipher, byte[] input) {
        int blockSize = blockCipher.getBlockSize();
        int blockRoundedSize = (input.length + (blockSize - 1)) & ~(blockSize - 1);

        byte[] blockRoundedInputData = Arrays.copyOf(input, blockRoundedSize);
        byte[] blockRoundedOutputData = new byte[blockRoundedSize];

        for (int offset = 0; offset < blockRoundedSize; offset += blockSize) {
            blockCipher.processBlock(blockRoundedInputData, offset, blockRoundedOutputData, offset);
        }

        return blockRoundedOutputData;
    }

    private static byte[] decryptAes(byte[] encryptedData, byte[] aesKey) {
        BlockCipher aesEngine = new AESLightEngine();
        aesEngine.init(false, new KeyParameter(aesKey));
        return performBlockCipher(aesEngine, encryptedData);
    }

    private static byte[] encryptAes(byte[] plaintextData, byte[] aesKey) {
        BlockCipher aesEngine = new AESLightEngine();
        aesEngine.init(true, new KeyParameter(aesKey));
        return performBlockCipher(aesEngine, plaintextData);
    }

    private static byte[] generateAesKey(PairingHashAlgorithm hashAlgo, byte[] keyData) {
        return Arrays.copyOf(hashAlgo.hashData(keyData), 16);
    }

    private static byte[] concatBytes(byte[] a, byte[] b) {
        byte[] c = new byte[a.length + b.length];
        System.arraycopy(a, 0, c, 0, a.length);
        System.arraycopy(b, 0, c, a.length, b.length);
        return c;
    }

    public static String generatePinString() {
        SecureRandom r = new SecureRandom();
        return String.format((Locale)null, "%d%d%d%d",
                r.nextInt(10), r.nextInt(10),
                r.nextInt(10), r.nextInt(10));
    }

    public X509Certificate getPairedCert() {
        return serverCert;
    }

    /**
     * Apollo holds the first /pair response open while the user enters the PIN. Some Apollo/Simple-
     * Web-Server combinations can close that idle response after only a few seconds, which OkHttp
     * reports as "unexpected end of stream" even though the pairing session still exists server-
     * side. Reissuing getservercert with the same unique ID/salt/certificate is safe for Apollo: it
     * reuses the pending session and replaces its stored response handle.
     *
     * Only this pre-PIN wait is retried. Once cryptographic challenge exchange begins, any network
     * failure remains fatal so a broken or out-of-order handshake is never silently replayed.
     */
    private String executeInitialPairingWait(String pairingArguments) throws IOException {
        long deadline = System.nanoTime() + INITIAL_PAIR_RETRY_WINDOW_MS * 1_000_000L;
        int retryCount = 0;

        while (true) {
            try {
                return http.executePairingCommand(pairingArguments, false);
            } catch (IOException e) {
                if (!isRetryableInitialPairingDisconnect(e) ||
                        System.nanoTime() >= deadline ||
                        Thread.currentThread().isInterrupted()) {
                    throw e;
                }

                retryCount++;
                LimeLog.warning("Pairing PIN wait connection closed early; reconnecting pending " +
                        "pair request (attempt " + retryCount + "): " + e.getMessage());
                try {
                    Thread.sleep(INITIAL_PAIR_RETRY_DELAY_MS);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Pairing interrupted while reconnecting PIN wait", interrupted);
                }
            }
        }
    }

    static boolean isRetryableInitialPairingDisconnect(IOException error) {
        // HTTP status failures are deliberate server responses, not an idle socket disappearing.
        if (error instanceof HostHttpResponseException) {
            return false;
        }

        Throwable current = error;
        while (current != null) {
            if (current instanceof EOFException) {
                return true;
            }

            String message = current.getMessage();
            String normalized = message == null ? "" : message.toLowerCase(Locale.ROOT);

            if (current instanceof ProtocolException && normalized.contains("unexpected end of stream")) {
                return true;
            }
            if (current instanceof SocketException &&
                    (normalized.contains("connection reset") ||
                            normalized.contains("connection abort") ||
                            normalized.contains("broken pipe") ||
                            normalized.contains("socket closed"))) {
                return true;
            }
            if (normalized.contains("unexpected end of stream")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    public PairState pair(String serverInfo, String pin, String passphrase) throws IOException, XmlPullParserException {
        PairingHashAlgorithm hashAlgo;

        int serverMajorVersion = http.getServerMajorVersion(serverInfo);
        LimeLog.info("Pairing with server generation: "+serverMajorVersion);
        if (serverMajorVersion >= 7) {
            hashAlgo = new Sha256PairingHash();
        }
        else {
            hashAlgo = new Sha1PairingHash();
        }

        byte[] salt = generateRandomBytes(16);
        byte[] aesKey = generateAesKey(hashAlgo, saltPin(salt, pin));
        String saltStr = bytesToHex(salt);

        String pairingArguments = "phrase=getservercert&salt="+
                saltStr+"&clientcert="+bytesToHex(pemCertBytes);

        if (passphrase != null) {
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                String plainText = pin + saltStr + passphrase;
                byte[] hash = digest.digest(plainText.getBytes());

                StringBuilder hexString = new StringBuilder();
                for (byte b : hash) {
                    hexString.append(String.format("%02X", b));
                }

                pairingArguments += "&otpauth=" + hexString;
            } catch (NoSuchAlgorithmException e) {
                throw new RuntimeException(e);
            }
        }

        // Keep waiting for the PIN even if Apollo drops and recreates the idle HTTP response.
        String getCert = executeInitialPairingWait(pairingArguments);
        if (!NvHTTP.getXmlString(getCert, "paired", true).equals("1")) {
            return PairState.FAILED;
        }

        serverCert = extractPlainCert(getCert);
        if (serverCert == null) {
            http.unpair();
            return PairState.ALREADY_IN_PROGRESS;
        }

        http.setServerCert(serverCert);

        byte[] randomChallenge = generateRandomBytes(16);
        byte[] encryptedChallenge = encryptAes(randomChallenge, aesKey);

        String challengeResp = http.executePairingCommand("clientchallenge="+bytesToHex(encryptedChallenge), true);
        if (!NvHTTP.getXmlString(challengeResp, "paired", true).equals("1")) {
            http.unpair();
            return PairState.FAILED;
        }

        byte[] encServerChallengeResponse = hexToBytes(NvHTTP.getXmlString(challengeResp, "challengeresponse", true));
        byte[] decServerChallengeResponse = decryptAes(encServerChallengeResponse, aesKey);

        byte[] serverResponse = Arrays.copyOfRange(decServerChallengeResponse, 0, hashAlgo.getHashLength());
        byte[] serverChallenge = Arrays.copyOfRange(decServerChallengeResponse, hashAlgo.getHashLength(), hashAlgo.getHashLength() + 16);

        byte[] clientSecret = generateRandomBytes(16);
        byte[] challengeRespHash = hashAlgo.hashData(concatBytes(concatBytes(serverChallenge, cert.getSignature()), clientSecret));
        byte[] challengeRespEncrypted = encryptAes(challengeRespHash, aesKey);
        String secretResp = http.executePairingCommand("serverchallengeresp="+bytesToHex(challengeRespEncrypted), true);
        if (!NvHTTP.getXmlString(secretResp, "paired", true).equals("1")) {
            http.unpair();
            return PairState.FAILED;
        }

        byte[] serverSecretResp = hexToBytes(NvHTTP.getXmlString(secretResp, "pairingsecret", true));
        byte[] serverSecret = Arrays.copyOfRange(serverSecretResp, 0, 16);
        byte[] serverSignature = Arrays.copyOfRange(serverSecretResp, 16, serverSecretResp.length);

        if (!verifySignature(serverSecret, serverSignature, serverCert)) {
            http.unpair();
            return PairState.FAILED;
        }

        byte[] serverChallengeRespHash = hashAlgo.hashData(concatBytes(concatBytes(randomChallenge, serverCert.getSignature()), serverSecret));
        if (!Arrays.equals(serverChallengeRespHash, serverResponse)) {
            http.unpair();
            return PairState.PIN_WRONG;
        }

        byte[] clientPairingSecret = concatBytes(clientSecret, signData(clientSecret, pk));
        String clientSecretResp = http.executePairingCommand("clientpairingsecret="+bytesToHex(clientPairingSecret), true);
        if (!NvHTTP.getXmlString(clientSecretResp, "paired", true).equals("1")) {
            http.unpair();
            return PairState.FAILED;
        }

        String pairChallenge = http.executePairingChallenge();
        if (!NvHTTP.getXmlString(pairChallenge, "paired", true).equals("1")) {
            http.unpair();
            return PairState.FAILED;
        }

        return PairState.PAIRED;
    }

    private interface PairingHashAlgorithm {
        int getHashLength();
        byte[] hashData(byte[] data);
    }

    private static class Sha1PairingHash implements PairingHashAlgorithm {
        public int getHashLength() {
            return 20;
        }

        public byte[] hashData(byte[] data) {
            try {
                MessageDigest md = MessageDigest.getInstance("SHA-1");
                return md.digest(data);
            }
            catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        }
    }

    private static class Sha256PairingHash implements PairingHashAlgorithm {
        public int getHashLength() {
            return 32;
        }

        public byte[] hashData(byte[] data) {
            try {
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                return md.digest(data);
            }
            catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        }
    }
}
