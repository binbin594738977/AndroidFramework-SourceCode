/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.org.bouncycastle.jce.provider;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.security.cert.CertificateFactory;
import java.security.cert.Certificate;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.HashSet;
import java.util.Set;
import junit.framework.TestCase;
import com.android.org.bouncycastle.jce.provider.CertBlacklist;
import com.android.org.bouncycastle.crypto.Digest;
import com.android.org.bouncycastle.util.encoders.Base64;
import com.android.org.bouncycastle.util.encoders.Hex;

public class CertBlacklistTest extends TestCase {

    private File tmpFile;

    private Set<String> DEFAULT_PUBKEYS;
    private Set<String> DEFAULT_SERIALS;

    public static final String TEST_CERT = "" +
                    "MIIDsjCCAxugAwIBAgIJAPLf2gS0zYGUMA0GCSqGSIb3DQEBBQUAMIGYMQswCQYDVQQGEwJVUzET" +
                    "MBEGA1UECBMKQ2FsaWZvcm5pYTEWMBQGA1UEBxMNTW91bnRhaW4gVmlldzEPMA0GA1UEChMGR29v" +
                    "Z2xlMRAwDgYDVQQLEwd0ZXN0aW5nMRYwFAYDVQQDEw1HZXJlbXkgQ29uZHJhMSEwHwYJKoZIhvcN" +
                    "AQkBFhJnY29uZHJhQGdvb2dsZS5jb20wHhcNMTIwNzE0MTc1MjIxWhcNMTIwODEzMTc1MjIxWjCB" +
                    "mDELMAkGA1UEBhMCVVMxEzARBgNVBAgTCkNhbGlmb3JuaWExFjAUBgNVBAcTDU1vdW50YWluIFZp" +
                    "ZXcxDzANBgNVBAoTBkdvb2dsZTEQMA4GA1UECxMHdGVzdGluZzEWMBQGA1UEAxMNR2VyZW15IENv" +
                    "bmRyYTEhMB8GCSqGSIb3DQEJARYSZ2NvbmRyYUBnb29nbGUuY29tMIGfMA0GCSqGSIb3DQEBAQUA" +
                    "A4GNADCBiQKBgQCjGGHATBYlmas+0sEECkno8LZ1KPglb/mfe6VpCT3GhSr+7br7NG/ZwGZnEhLq" +
                    "E7YIH4fxltHmQC3Tz+jM1YN+kMaQgRRjo/LBCJdOKaMwUbkVynAH6OYsKevjrOPk8lfM5SFQzJMG" +
                    "sA9+Tfopr5xg0BwZ1vA/+E3mE7Tr3M2UvwIDAQABo4IBADCB/TAdBgNVHQ4EFgQUhzkS9E6G+x8W" +
                    "L4EsmRjDxu28tHUwgc0GA1UdIwSBxTCBwoAUhzkS9E6G+x8WL4EsmRjDxu28tHWhgZ6kgZswgZgx" +
                    "CzAJBgNVBAYTAlVTMRMwEQYDVQQIEwpDYWxpZm9ybmlhMRYwFAYDVQQHEw1Nb3VudGFpbiBWaWV3" +
                    "MQ8wDQYDVQQKEwZHb29nbGUxEDAOBgNVBAsTB3Rlc3RpbmcxFjAUBgNVBAMTDUdlcmVteSBDb25k" +
                    "cmExITAfBgkqhkiG9w0BCQEWEmdjb25kcmFAZ29vZ2xlLmNvbYIJAPLf2gS0zYGUMAwGA1UdEwQF" +
                    "MAMBAf8wDQYJKoZIhvcNAQEFBQADgYEAYiugFDmbDOQ2U/+mqNt7o8ftlEo9SJrns6O8uTtK6AvR" +
                    "orDrR1AXTXkuxwLSbmVfedMGOZy7Awh7iZa8hw5x9XmUudfNxvmrKVEwGQY2DZ9PXbrnta/dwbhK" +
                    "mWfoepESVbo7CKIhJp8gRW0h1Z55ETXD57aGJRvQS4pxkP8ANhM=";

    public CertBlacklistTest() throws IOException {
        tmpFile = File.createTempFile("test", "");
        DEFAULT_PUBKEYS = getDefaultPubkeys();
        DEFAULT_SERIALS = getDefaultSerials();
        tmpFile.delete();
    }

    @Override
    public void setUp() throws Exception {
        super.setUp();
        tmpFile = File.createTempFile("test", "");
    }

    @Override
    public void tearDown() throws Exception {
        try {
            tmpFile.delete();
        } finally {
            super.tearDown();
        }
    }

    private Set<String> getPubkeyBlacklist(String path) throws IOException {
        // set our blacklist path
        CertBlacklist bl = new CertBlacklist(path, CertBlacklist.DEFAULT_SERIAL_BLACKLIST_PATH);
        // call readPubkeyBlacklist
        Set<byte[]> arr = bl.pubkeyBlacklist;
        // convert the results to a hashset of strings
        Set<String> results = new HashSet<String>();
        for (byte[] value: arr) {
            results.add(new String(value));
        }
        return results;
    }

    private Set<String> getSerialBlacklist(String path) throws IOException {
        // set our blacklist path
        CertBlacklist bl = new CertBlacklist(CertBlacklist.DEFAULT_PUBKEY_BLACKLIST_PATH, path);
        // call readPubkeyBlacklist
        Set<BigInteger> arr = bl.serialBlacklist;
        // convert the results to a hashset of strings
        Set<String> results = new HashSet<String>();
        for (BigInteger value: arr) {
            results.add(value.toString(16));
        }
        return results;
    }

    private String getHash(PublicKey publicKey) throws Exception {
        byte[] encoded = publicKey.getEncoded();
        MessageDigest digest = MessageDigest.getInstance("SHA1");
        byte[] hexlifiedHash = Hex.encode(digest.digest(encoded));
        return new String(hexlifiedHash);
    }

    private Set<String> getDefaultPubkeys() throws IOException {
        return getPubkeyBlacklist("");
    }

    private Set<String> getDefaultSerials() throws IOException {
        return getSerialBlacklist("");
    }

    private Set<String> getCurrentPubkeyBlacklist() throws IOException {
        return getPubkeyBlacklist(tmpFile.getCanonicalPath());
    }

    private Set<String> getCurrentSerialBlacklist() throws IOException {
        return getSerialBlacklist(tmpFile.getCanonicalPath());
    }

    private void blacklistToFile(String blacklist) throws IOException {
        FileOutputStream out = new FileOutputStream(tmpFile);
        out.write(blacklist.toString().getBytes());
        out.close();
    }

    private void writeBlacklist(HashSet<String> values) throws IOException {
        StringBuilder result = new StringBuilder();
        // join the values into a string
        for (String value : values) {
            if (result.length() != 0) {
                result.append(",");
            }
            result.append(value);
        }
        blacklistToFile(result.toString());
    }

    private PublicKey createPublicKey(String cert) throws Exception {
        byte[] derCert = Base64.decode(cert.getBytes());
        InputStream istream = new ByteArrayInputStream(derCert);
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        return cf.generateCertificate(istream).getPublicKey();
    }

    public void testPubkeyBlacklistLegit() throws Exception {
        // build the blacklist
        HashSet<String> bl = new HashSet<String>();
        bl.add("6ccabd7db47e94a5759901b6a7dfd45d1c091ccc");
        // write the blacklist
        writeBlacklist(bl);
        // add the default pubkeys into the bl
        bl.addAll(DEFAULT_PUBKEYS);
        // do the test
        assertEquals(bl, getCurrentPubkeyBlacklist());
    }

    public void testLegitPubkeyIsntBlacklisted() throws Exception {
        // build the public key
        PublicKey pk = createPublicKey(TEST_CERT);
        // write that to the test blacklist
        writeBlacklist(new HashSet<String>());
        // set our blacklist path
        CertBlacklist bl = new CertBlacklist(tmpFile.getCanonicalPath(),
                                             CertBlacklist.DEFAULT_SERIAL_BLACKLIST_PATH);
        // check to make sure it isn't blacklisted
        assertEquals(bl.isPublicKeyBlackListed(pk), false);
    }

    public void testPubkeyIsBlacklisted() throws Exception {
        // build the public key
        PublicKey pk = createPublicKey(TEST_CERT);
        // get its hash
        String hash = getHash(pk);
        // write that to the test blacklist
        HashSet<String> testBlackList = new HashSet<String>();
        testBlackList.add(hash);
        writeBlacklist(testBlackList);
        // set our blacklist path
        CertBlacklist bl = new CertBlacklist(tmpFile.getCanonicalPath(),
                                             CertBlacklist.DEFAULT_SERIAL_BLACKLIST_PATH);
        // check to make sure it isn't blacklited
        assertTrue(bl.isPublicKeyBlackListed(pk));
    }

    public void testSerialBlacklistLegit() throws IOException {
        // build the blacklist
        HashSet<String> bl = new HashSet<String>();
        bl.add("22e514121e61c643b1e9b06bd4b9f7d0");
        // write the blacklist
        writeBlacklist(bl);
        // add the default serials into the bl
        bl.addAll(DEFAULT_SERIALS);
        // do the test
        assertEquals(bl, getCurrentSerialBlacklist());
    }

    public void testPubkeyBlacklistMultipleLegit() throws IOException {
        // build the blacklist
        HashSet<String> bl = new HashSet<String>();
        bl.add("6ccabd7db47e94a5759901b6a7dfd45d1c091ccc");
        bl.add("6ccabd7db47e94a5759901b6a7dfd45d1c091ccd");
        // write the blacklist
        writeBlacklist(bl);
        // add the default pubkeys into the bl
        bl.addAll(DEFAULT_PUBKEYS);
        // do the test
        assertEquals(bl, getCurrentPubkeyBlacklist());
    }

    public void testSerialBlacklistMultipleLegit() throws IOException {
        // build the blacklist
        HashSet<String> bl = new HashSet<String>();
        bl.add("22e514121e61c643b1e9b06bd4b9f7d0");
        bl.add("22e514121e61c643b1e9b06bd4b9f7d1");
        // write the blacklist
        writeBlacklist(bl);
        // add the default serials into the bl
        bl.addAll(DEFAULT_SERIALS);
        // do the test
        assertEquals(bl, getCurrentSerialBlacklist());
    }

    public void testPubkeyBlacklistMultipleBad() throws IOException {
        // build the blacklist
        HashSet<String> bl = new HashSet<String>();
        bl.add("6ccabd7db47e94a5759901b6a7dfd45d1c091ccc");
        bl.add("");
        bl.add("6ccabd7db47e94a5759901b6a7dfd45d1c091ccd");
        // write the blacklist
        writeBlacklist(bl);
        // add the default pubkeys into the bl
        bl.addAll(DEFAULT_PUBKEYS);
        // remove the bad one
        bl.remove("");
        // do the test- results should be all but the bad one are handled
        assertEquals(bl, getCurrentPubkeyBlacklist());
    }

    public void testSerialBlacklistMultipleBad() throws IOException {
        // build the blacklist
        HashSet<String> bl = new HashSet<String>();
        bl.add("22e514121e61c643b1e9b06bd4b9f7d0");
        bl.add("");
        bl.add("22e514121e61c643b1e9b06bd4b9f7d1");
        // write the blacklist
        writeBlacklist(bl);
        // add the default serials into the bl
        bl.addAll(DEFAULT_SERIALS);
        // remove the bad one
        bl.remove("");
        // do the test- results should be all but the bad one are handled
        assertEquals(bl, getCurrentSerialBlacklist());
    }

    public void testPubkeyBlacklistDoesntExist() throws IOException {
        assertEquals(DEFAULT_PUBKEYS, getCurrentPubkeyBlacklist());
    }

    public void testSerialBlacklistDoesntExist() throws IOException {
        assertEquals(DEFAULT_SERIALS, getCurrentSerialBlacklist());
    }

    public void testPubkeyBlacklistNotHexValues() throws IOException {
        // build the blacklist
        HashSet<String> bl = new HashSet<String>();
        bl.add("ZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZ");
        // write the blacklist
        writeBlacklist(bl);
        // do the test
        assertEquals(DEFAULT_PUBKEYS, getCurrentPubkeyBlacklist());
    }

    public void testSerialBlacklistNotHexValues() throws IOException {
        // build the blacklist
        HashSet<String> bl = new HashSet<String>();
        bl.add("ZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZ");
        // write the blacklist
        writeBlacklist(bl);
        // do the test
        assertEquals(DEFAULT_SERIALS, getCurrentSerialBlacklist());
    }

    public void testPubkeyBlacklistIncorrectLength() throws IOException {
        // build the blacklist
        HashSet<String> bl = new HashSet<String>();
        bl.add("6ccabd7db47e94a5759901b6a7dfd45d1c091cc");
        // write the blacklist
        writeBlacklist(bl);
        // do the test
        assertEquals(DEFAULT_PUBKEYS, getCurrentPubkeyBlacklist());
    }

    public void testSerialBlacklistZero() throws IOException {
        // build the blacklist
        HashSet<String> bl = new HashSet<String>();
        bl.add("0");
        // write the blacklist
        writeBlacklist(bl);
        // add the default serials
        bl.addAll(DEFAULT_SERIALS);
        // do the test
        assertEquals(bl, getCurrentSerialBlacklist());
    }

    public void testSerialBlacklistNegative() throws IOException {
        // build the blacklist
        HashSet<String> bl = new HashSet<String>();
        bl.add("-1");
        // write the blacklist
        writeBlacklist(bl);
        // add the default serials
        bl.addAll(DEFAULT_SERIALS);
        // do the test
        assertEquals(bl, getCurrentSerialBlacklist());
    }
}
