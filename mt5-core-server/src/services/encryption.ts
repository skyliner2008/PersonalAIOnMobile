import crypto from 'node:crypto';
import { config } from '../config.js';

const ALGORITHM = 'aes-256-gcm';
const IV_LENGTH = 12; // Standard for GCM
const AUTH_TAG_LENGTH = 16;

/**
 * Encrypt a string using AES-256-GCM.
 * Format: iv (hex) + authTag (hex) + ciphertext (hex)
 */
export function encrypt(text: string): string {
  if (!text) return '';
  
  // Key must be exactly 32 bytes for aes-256
  const key = crypto.scryptSync(config.dbEncryptionKey, 'salt', 32);
  const iv = crypto.randomBytes(IV_LENGTH);
  const cipher = crypto.createCipheriv(ALGORITHM, key, iv);
  
  let encrypted = cipher.update(text, 'utf8', 'hex');
  encrypted += cipher.final('hex');
  
  const authTag = cipher.getAuthTag().toString('hex');
  
  return `${iv.toString('hex')}${authTag}${encrypted}`;
}

/**
 * Decrypt a string using AES-256-GCM.
 */
export function decrypt(encryptedText: string): string {
  if (!encryptedText) return '';
  
  try {
    const key = crypto.scryptSync(config.dbEncryptionKey, 'salt', 32);
    
    // Extract parts
    const ivHex = encryptedText.slice(0, IV_LENGTH * 2);
    const authTagHex = encryptedText.slice(IV_LENGTH * 2, (IV_LENGTH + AUTH_TAG_LENGTH) * 2);
    const ciphertextHex = encryptedText.slice((IV_LENGTH + AUTH_TAG_LENGTH) * 2);
    
    const iv = Buffer.from(ivHex, 'hex');
    const authTag = Buffer.from(authTagHex, 'hex');
    const decipher = crypto.createDecipheriv(ALGORITHM, key, iv);
    
    decipher.setAuthTag(authTag);
    
    let decrypted = decipher.update(ciphertextHex, 'hex', 'utf8');
    decrypted += decipher.final('utf8');
    
    return decrypted;
  } catch (err) {
    // If decryption fails, it might be an unencrypted string (legacy)
    // In a real production system, you might want to log this or handle it strictly.
    return encryptedText;
  }
}
