/*
 * Copyright (C) 2015 The Android Open Source Project
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
 * limitations under the License
 */

package libcore.util;

import sun.security.pkcs.PKCS7;
import sun.security.pkcs.SignerInfo;

import java.io.IOException;
import java.io.InputStream;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.SignatureException;
import java.security.cert.X509Certificate;
import java.util.Set;

public class RecoverySystem {
    private RecoverySystem() {
    }

    /**
     * Verifies that the signature computed from {@code contentStream} matches
     * that specified in {@code blockStream}. The public key of the certificates specified
     * in the PCKS7 block must match  
     */
    public static void verify(InputStream blockStream, InputStream contentStream,
                              Set<X509Certificate> trustedCerts)
            throws IOException, SignatureException, NoSuchAlgorithmException {
        PKCS7 block = new PKCS7(blockStream);

        // Take the first certificate from the signature (packages
        // should contain only one).
        X509Certificate[] certificates = block.getCertificates();
        if (certificates == null || certificates.length == 0) {
            throw new SignatureException("signature contains no certificates");
        }
        X509Certificate cert = certificates[0];
        PublicKey signatureKey = cert.getPublicKey();

        SignerInfo[] signerInfos = block.getSignerInfos();
        if (signerInfos == null || signerInfos.length == 0) {
            throw new SignatureException("signature contains no signedData");
        }
        SignerInfo signerInfo = signerInfos[0];

        boolean verified = false;
        for (X509Certificate c : trustedCerts) {
            if (c.getPublicKey().equals(signatureKey)) {
                verified = true;
                break;
            }
        }

        if (!verified) {
            throw new SignatureException("signature doesn't match any trusted key");
        }

        SignerInfo verifyResult = block.verify(signerInfo, contentStream);
        if (verifyResult == null) {
            throw new SignatureException("signature digest verification failed");
        }
    }
}
