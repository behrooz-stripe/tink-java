// Copyright 2020 Google LLC
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
package com.google.crypto.tink.jwt;

import static java.nio.charset.StandardCharsets.US_ASCII;

import com.google.crypto.tink.AccessesPartialKey;
import com.google.crypto.tink.PublicKeyVerify;
import com.google.crypto.tink.config.internal.TinkFipsUtil;
import com.google.crypto.tink.internal.KeyTypeManager;
import com.google.crypto.tink.internal.PrimitiveConstructor;
import com.google.crypto.tink.internal.PrimitiveFactory;
import com.google.crypto.tink.proto.JwtRsaSsaPssAlgorithm;
import com.google.crypto.tink.proto.JwtRsaSsaPssPublicKey;
import com.google.crypto.tink.proto.KeyData.KeyMaterialType;
import com.google.crypto.tink.signature.RsaSsaPssParameters;
import com.google.crypto.tink.signature.RsaSsaPssPublicKey;
import com.google.crypto.tink.subtle.EngineFactory;
import com.google.crypto.tink.subtle.Enums;
import com.google.crypto.tink.subtle.RsaSsaPssVerifyJce;
import com.google.crypto.tink.subtle.Validators;
import com.google.gson.JsonObject;
import com.google.protobuf.ByteString;
import com.google.protobuf.ExtensionRegistryLite;
import com.google.protobuf.InvalidProtocolBufferException;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.util.Optional;

/**
 * This key manager produces new instances of {@code JwtRsaSsaPss1Verify}. It doesn't support key
 * generation.
 */
class JwtRsaSsaPssVerifyKeyManager extends KeyTypeManager<JwtRsaSsaPssPublicKey> {
  private static RsaSsaPssParameters.HashType hashTypeForAlgorithm(
      JwtRsaSsaPssParameters.Algorithm algorithm) throws GeneralSecurityException {
    if (algorithm.equals(JwtRsaSsaPssParameters.Algorithm.PS256)) {
      return RsaSsaPssParameters.HashType.SHA256;
    }
    if (algorithm.equals(JwtRsaSsaPssParameters.Algorithm.PS384)) {
      return RsaSsaPssParameters.HashType.SHA384;
    }
    if (algorithm.equals(JwtRsaSsaPssParameters.Algorithm.PS512)) {
      return RsaSsaPssParameters.HashType.SHA512;
    }
    throw new GeneralSecurityException("unknown algorithm " + algorithm);
  }

  static final int saltLengthForPssAlgorithm(JwtRsaSsaPssParameters.Algorithm algorithm)
      throws GeneralSecurityException {
    if (algorithm.equals(JwtRsaSsaPssParameters.Algorithm.PS256)) {
      return 32;
    }
    if (algorithm.equals(JwtRsaSsaPssParameters.Algorithm.PS384)) {
      return 48;
    }
    if (algorithm.equals(JwtRsaSsaPssParameters.Algorithm.PS512)) {
      return 64;
    }
    throw new GeneralSecurityException("unknown algorithm " + algorithm);
  }

  @AccessesPartialKey
  static RsaSsaPssPublicKey toRsaSsaPssPublicKey(
      com.google.crypto.tink.jwt.JwtRsaSsaPssPublicKey publicKey) throws GeneralSecurityException {
    RsaSsaPssParameters rsaSsaPssParameters =
        RsaSsaPssParameters.builder()
            .setModulusSizeBits(publicKey.getParameters().getModulusSizeBits())
            .setPublicExponent(publicKey.getParameters().getPublicExponent())
            .setSigHashType(hashTypeForAlgorithm(publicKey.getParameters().getAlgorithm()))
            .setMgf1HashType(hashTypeForAlgorithm(publicKey.getParameters().getAlgorithm()))
            .setSaltLengthBytes(saltLengthForPssAlgorithm(publicKey.getParameters().getAlgorithm()))
            .setVariant(RsaSsaPssParameters.Variant.NO_PREFIX)
            .build();
    return RsaSsaPssPublicKey.builder()
        .setParameters(rsaSsaPssParameters)
        .setModulus(publicKey.getModulus())
        .build();
  }

  @SuppressWarnings("Immutable") // RsaSsaPssVerifyJce.create returns an immutable verifier.
  static JwtPublicKeyVerify createFullPrimitive(
      com.google.crypto.tink.jwt.JwtRsaSsaPssPublicKey publicKey) throws GeneralSecurityException {
    RsaSsaPssPublicKey rsaSsaPssPublicKey = toRsaSsaPssPublicKey(publicKey);
    final PublicKeyVerify verifier = RsaSsaPssVerifyJce.create(rsaSsaPssPublicKey);

    return new JwtPublicKeyVerify() {
      @Override
      public VerifiedJwt verifyAndDecode(String compact, JwtValidator validator)
          throws GeneralSecurityException {
        JwtFormat.Parts parts = JwtFormat.splitSignedCompact(compact);
        verifier.verify(parts.signatureOrMac, parts.unsignedCompact.getBytes(US_ASCII));
        JsonObject parsedHeader = JsonUtil.parseJson(parts.header);
        JwtFormat.validateHeader(
            parsedHeader,
            publicKey.getParameters().getAlgorithm().getStandardName(),
            publicKey.getKid(),
            publicKey.getParameters().allowKidAbsent());
        RawJwt token = RawJwt.fromJsonPayload(JwtFormat.getTypeHeader(parsedHeader), parts.payload);
        return validator.validate(token);
      }
    };
  }

  static final PrimitiveConstructor<
          com.google.crypto.tink.jwt.JwtRsaSsaPssPublicKey, JwtPublicKeyVerify>
      PRIMITIVE_CONSTRUCTOR =
          PrimitiveConstructor.create(
              JwtRsaSsaPssVerifyKeyManager::createFullPrimitive,
              com.google.crypto.tink.jwt.JwtRsaSsaPssPublicKey.class,
              JwtPublicKeyVerify.class);

  // Note: each algorithm defines not just the modulo size, but also the
  // hash length and salt length to use.
  // See https://www.rfc-editor.org/rfc/rfc7518.html#section-3.5
  static final Enums.HashType hashForPssAlgorithm(JwtRsaSsaPssAlgorithm algorithm)
      throws GeneralSecurityException {
    switch (algorithm) {
      case PS256:
        return Enums.HashType.SHA256;
      case PS384:
        return Enums.HashType.SHA384;
      case PS512:
        return Enums.HashType.SHA512;
      default:
        throw new GeneralSecurityException("unknown algorithm " + algorithm.name());
    }
  }

  static final int saltLengthForPssAlgorithm(JwtRsaSsaPssAlgorithm algorithm)
      throws GeneralSecurityException {
    switch (algorithm) {
      case PS256:
        return 32;
      case PS384:
        return 48;
      case PS512:
        return 64;
      default:
        throw new GeneralSecurityException("unknown algorithm " + algorithm.name());
    }
  }

  private static final RSAPublicKey createPublicKey(JwtRsaSsaPssPublicKey keyProto)
      throws GeneralSecurityException {
    java.security.KeyFactory kf = EngineFactory.KEY_FACTORY.getInstance("RSA");
    BigInteger modulus = new BigInteger(1, keyProto.getN().toByteArray());
    BigInteger exponent = new BigInteger(1, keyProto.getE().toByteArray());
    return (RSAPublicKey) kf.generatePublic(new RSAPublicKeySpec(modulus, exponent));
  }

  public JwtRsaSsaPssVerifyKeyManager() {
    super(
        JwtRsaSsaPssPublicKey.class,
        new PrimitiveFactory<JwtPublicKeyVerifyInternal, JwtRsaSsaPssPublicKey>(
            JwtPublicKeyVerifyInternal.class) {
          @Override
          public JwtPublicKeyVerifyInternal getPrimitive(JwtRsaSsaPssPublicKey keyProto)
              throws GeneralSecurityException {
            RSAPublicKey publickey = createPublicKey(keyProto);
            Enums.HashType hash = hashForPssAlgorithm(keyProto.getAlgorithm());
            int saltLength = saltLengthForPssAlgorithm(keyProto.getAlgorithm());
            final RsaSsaPssVerifyJce verifier =
                new RsaSsaPssVerifyJce(publickey, hash, hash, saltLength);
            final String algorithmName = keyProto.getAlgorithm().name();
            final Optional<String> customKidFromRsaSsaPssPublicKey =
                keyProto.hasCustomKid()
                    ? Optional.of(keyProto.getCustomKid().getValue())
                    : Optional.empty();

            return new JwtPublicKeyVerifyInternal() {
              @Override
              public VerifiedJwt verifyAndDecodeWithKid(
                  String compact, JwtValidator validator, Optional<String> kid)
                  throws GeneralSecurityException {
                JwtFormat.Parts parts = JwtFormat.splitSignedCompact(compact);
                verifier.verify(parts.signatureOrMac, parts.unsignedCompact.getBytes(US_ASCII));
                JsonObject parsedHeader = JsonUtil.parseJson(parts.header);
                JwtFormat.validateHeader(
                    algorithmName, kid, customKidFromRsaSsaPssPublicKey, parsedHeader);
                RawJwt token =
                    RawJwt.fromJsonPayload(JwtFormat.getTypeHeader(parsedHeader), parts.payload);
                return validator.validate(token);
              }
            };
          }
        });
  }

  @Override
  public String getKeyType() {
    return "type.googleapis.com/google.crypto.tink.JwtRsaSsaPssPublicKey";
  }

  @Override
  public TinkFipsUtil.AlgorithmFipsCompatibility fipsStatus() {
    return TinkFipsUtil.AlgorithmFipsCompatibility.ALGORITHM_NOT_FIPS;
  }

  @Override
  public int getVersion() {
    return 0;
  }

  @Override
  public KeyMaterialType keyMaterialType() {
    return KeyMaterialType.ASYMMETRIC_PUBLIC;
  }

  @Override
  public JwtRsaSsaPssPublicKey parseKey(ByteString byteString)
      throws InvalidProtocolBufferException {
    return JwtRsaSsaPssPublicKey.parseFrom(byteString, ExtensionRegistryLite.getEmptyRegistry());
  }

  @Override
  public void validateKey(JwtRsaSsaPssPublicKey pubKey) throws GeneralSecurityException {
    Validators.validateVersion(pubKey.getVersion(), getVersion());
    Validators.validateRsaModulusSize(new BigInteger(1, pubKey.getN().toByteArray()).bitLength());
    Validators.validateRsaPublicExponent(new BigInteger(1, pubKey.getE().toByteArray()));
  }
}
