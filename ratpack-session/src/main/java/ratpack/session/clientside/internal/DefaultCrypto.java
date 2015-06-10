/*
 * Copyright 2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ratpack.session.clientside.internal;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import ratpack.session.clientside.Crypto;
import ratpack.util.Exceptions;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;

public class DefaultCrypto implements Crypto {

  private final SecretKeySpec secretKeySpec;
  private final String algorithm;
  private final boolean isInitializationVectorRequired;

  public DefaultCrypto(byte[] key, String algorithm) {
    String[] parts = algorithm.split("/");
    this.secretKeySpec = new SecretKeySpec(key, parts[0]);
    this.algorithm = algorithm;
    this.isInitializationVectorRequired = parts.length > 1 && !parts[1].equalsIgnoreCase("ECB");
  }

  @Override
  public ByteBuf encrypt(ByteBuf message, ByteBufAllocator allocator) {
    return Exceptions.uncheck(() -> {
      Cipher cipher = Cipher.getInstance(algorithm);
      cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec);

      int blockSize = cipher.getBlockSize();
      int messageLength = message.readableBytes();
      int encMessageLength = cipher.getOutputSize(messageLength);

      ByteBuf paddedMessage = null;
      if (messageLength == encMessageLength && (encMessageLength % blockSize) != 0) {
        int paddedMessageSize = messageLength + blockSize - (messageLength % blockSize);
        paddedMessage = allocator.buffer(paddedMessageSize);
        paddedMessage.setZero(0, paddedMessageSize);
        paddedMessage.setBytes(0, message, messageLength);
        paddedMessage.writerIndex(paddedMessageSize);
        encMessageLength = cipher.getOutputSize(paddedMessageSize);
      }

      ByteBuf encMessage = allocator.buffer(encMessageLength);
      ByteBuffer nioBuffer = encMessage.internalNioBuffer(0, encMessageLength);
      cipher.doFinal(paddedMessage == null ? message.nioBuffer() : paddedMessage.nioBuffer(), nioBuffer);
      encMessage.writerIndex(encMessageLength);

      if (paddedMessage != null) {
        paddedMessage.release();
      }

      if (isInitializationVectorRequired) {
        byte[] ivBytes = cipher.getIV();
        ByteBuf iv = allocator.buffer(1 + ivBytes.length);
        iv.writeByte(ivBytes.length).writeBytes(ivBytes);
        return Unpooled.wrappedBuffer(2, iv, encMessage);
      } else {
        return encMessage;
      }
    });
  }

  @Override
  public ByteBuf decrypt(ByteBuf message, ByteBufAllocator allocator) {
    return Exceptions.uncheck(() -> {
      Cipher cipher = Cipher.getInstance(algorithm);

      if (isInitializationVectorRequired) {
        int ivByteLength = message.readByte();
        ByteBuf ivBytes = message.readBytes(ivByteLength);
        IvParameterSpec ivParameterSpec = new IvParameterSpec(ivBytes.array());
        ivBytes.release();

        cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, ivParameterSpec);
      } else {
        cipher.init(Cipher.DECRYPT_MODE, secretKeySpec);
      }

      int messageLength = message.readableBytes();
      ByteBuf decMessage = allocator.buffer(cipher.getOutputSize(messageLength));
      int count = cipher.doFinal(message.readBytes(messageLength).nioBuffer(), decMessage.internalNioBuffer(0, messageLength));
      for (int i = count - 1; i >= 0; i--) {
        if (decMessage.getByte(i) == 0x00) {
          count--;
        } else {
          break;
        }
      }
      decMessage.writerIndex(count);
      return decMessage;
    });
  }
}
