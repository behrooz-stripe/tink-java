// Copyright 2024 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//
////////////////////////////////////////////////////////////////////////////////

package com.google.crypto.tink.aead;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;

import com.google.crypto.tink.Aead;
import com.google.crypto.tink.BinaryKeysetWriter;
import com.google.crypto.tink.InsecureSecretKeyAccess;
import com.google.crypto.tink.KeysetHandle;
import com.google.crypto.tink.KeysetWriter;
import com.google.crypto.tink.LegacyKeysetSerialization;
import com.google.crypto.tink.RegistryConfiguration;
import java.io.ByteArrayOutputStream;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class XAesGcmKeyManagerTest {

  @BeforeClass
  public static void setUp() throws Exception {
    XAesGcmKeyManager.register(/* newKeyAllowed= */ true);
  }

  @Test
  public void xAesGcmKeyTypeIsRegistered() throws Exception {
    assertNotNull(
        KeysetHandle.generateNew(PredefinedAeadParameters.XAES_256_GCM_160_BIT_NONCE_NO_PREFIX));
  }

  @Test
  public void xAesGcmKeyCreator_generatesNewKey() throws Exception {
    XAesGcmKey key1 =
        (XAesGcmKey)
            KeysetHandle.generateNew(PredefinedAeadParameters.XAES_256_GCM_160_BIT_NONCE_NO_PREFIX)
                .getPrimary()
                .getKey();
    XAesGcmKey key2 =
        (XAesGcmKey)
            KeysetHandle.generateNew(PredefinedAeadParameters.XAES_256_GCM_160_BIT_NONCE_NO_PREFIX)
                .getPrimary()
                .getKey();

    assertNotEquals(key1, key2);
  }

  @Test
  public void xAesGcmKeyNamesTemplates_areRegistered() throws Exception {
    assertNotNull(
        KeysetHandle.newBuilder()
            .addEntry(
                KeysetHandle.generateEntryFromParametersName("XAES_256_GCM_160_BIT_NONCE_NO_PREFIX")
                    .withRandomId()
                    .makePrimary())
            .build());
  }

  @Test
  public void xAesGcmKeySerialization_isRegistered() throws Exception {
    KeysetHandle handle =
        KeysetHandle.generateNew(PredefinedAeadParameters.XAES_256_GCM_160_BIT_NONCE_NO_PREFIX);
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    KeysetWriter keysetWriter = BinaryKeysetWriter.withOutputStream(outputStream);
    LegacyKeysetSerialization.serializeKeyset(handle, keysetWriter, InsecureSecretKeyAccess.get());
  }

  @Test
  public void xAesGcmPrimitiveCreation() throws Exception {
    AeadConfig.register();
    KeysetHandle handle =
        KeysetHandle.generateNew(PredefinedAeadParameters.XAES_256_GCM_160_BIT_NONCE_NO_PREFIX);
    Aead xAesGcm = handle.getPrimitive(RegistryConfiguration.get(), Aead.class);
    String plaintext = "plaintext";
    String associatedData = "associatedData";
    byte[] ciphertext = xAesGcm.encrypt(plaintext.getBytes(UTF_8), associatedData.getBytes(UTF_8));
    assertArrayEquals(
        xAesGcm.decrypt(ciphertext, associatedData.getBytes(UTF_8)), plaintext.getBytes(UTF_8));
  }
}
