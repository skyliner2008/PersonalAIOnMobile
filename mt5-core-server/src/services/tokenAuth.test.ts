import { describe, it, expect, vi, beforeEach } from 'vitest';

// Mock config and db before importing tokenAuth
vi.mock('../config.js', () => ({
  config: {
    tokenPepper: 'test-pepper',
    adminToken: 'test-admin-token'
  }
}));

const { mockDb } = vi.hoisted(() => ({
  mockDb: {
    prepare: vi.fn().mockReturnThis(),
    get: vi.fn(),
    run: vi.fn(),
    all: vi.fn(),
  }
}));

vi.mock('../db.js', () => ({
  getDb: () => mockDb
}));

import { requireAdminToken, requireClientToken } from './tokenAuth.js';
import type { Request, Response } from 'express';

describe('tokenAuth', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe('requireAdminToken', () => {
    it('should call next() if valid admin token is provided in header', () => {
      const req = {
        headers: { 'x-admin-token': 'test-admin-token' }
      } as unknown as Request;
      const res = {
        status: vi.fn().mockReturnThis(),
        json: vi.fn()
      } as unknown as Response;
      const next = vi.fn();

      requireAdminToken(req, res, next);

      expect(next).toHaveBeenCalled();
      expect(res.status).not.toHaveBeenCalled();
    });

    it('should call next() if valid bearer token is provided', () => {
      const req = {
        headers: { 'authorization': 'Bearer test-admin-token' }
      } as unknown as Request;
      const res = {
        status: vi.fn().mockReturnThis(),
        json: vi.fn()
      } as unknown as Response;
      const next = vi.fn();

      requireAdminToken(req, res, next);

      expect(next).toHaveBeenCalled();
    });

    it('should return 401 if token is missing', () => {
      const req = {
        headers: {}
      } as unknown as Request;
      const res = {
        status: vi.fn().mockReturnThis(),
        json: vi.fn()
      } as unknown as Response;
      const next = vi.fn();

      requireAdminToken(req, res, next);

      expect(res.status).toHaveBeenCalledWith(401);
      expect(res.json).toHaveBeenCalledWith(expect.objectContaining({ error: 'Admin token required' }));
      expect(next).not.toHaveBeenCalled();
    });

    it('should return 401 if token is invalid', () => {
      const req = {
        headers: { 'x-admin-token': 'wrong-token' }
      } as unknown as Request;
      const res = {
        status: vi.fn().mockReturnThis(),
        json: vi.fn()
      } as unknown as Response;
      const next = vi.fn();

      requireAdminToken(req, res, next);

      expect(next).not.toHaveBeenCalled();
    });
  });

  describe('requireClientToken', () => {
    it('should call next() if valid client token is found in DB', () => {
      const req = {
        headers: { 'x-client-token': 'valid-token' },
        path: '/test',
        method: 'GET',
        ip: '127.0.0.1',
        query: {}
      } as unknown as Request;
      const res = {
        status: vi.fn().mockReturnThis(),
        json: vi.fn()
      } as unknown as Response;
      const next = vi.fn();

      // Mock DB response for findActiveTokenByRawToken
      mockDb.get.mockReturnValue({
        id: 10,
        label: 'Test App',
        token_hash: 'hashed',
        token_prefix: 'prefix',
        is_active: 1,
        expires_at: null
      });

      requireClientToken(req, res, next);

      expect(next).toHaveBeenCalled();
      expect((req as any).clientToken).toEqual({
        id: 10,
        label: 'Test App',
        prefix: 'prefix'
      });
      // Should log usage
      expect(mockDb.run).toHaveBeenCalledTimes(2); // One for last_used_at, one for api_token_usage
    });

    it('should return 401 if client token is missing', () => {
      const req = {
        headers: {},
        query: {}
      } as unknown as Request;
      const res = {
        status: vi.fn().mockReturnThis(),
        json: vi.fn()
      } as unknown as Response;
      const next = vi.fn();

      requireClientToken(req, res, next);

      expect(res.status).toHaveBeenCalledWith(401);
      expect(next).not.toHaveBeenCalled();
    });

    it('should return 403 if token is invalid or inactive', () => {
      const req = {
        headers: { 'x-client-token': 'invalid-token' },
        query: {}
      } as unknown as Request;
      const res = {
        status: vi.fn().mockReturnThis(),
        json: vi.fn()
      } as unknown as Response;
      const next = vi.fn();

      mockDb.get.mockReturnValue(null);

      requireClientToken(req, res, next);

      expect(res.status).toHaveBeenCalledWith(403);
      expect(res.json).toHaveBeenCalledWith(expect.objectContaining({ error: 'Token invalid or expired' }));
      expect(next).not.toHaveBeenCalled();
    });
  });
});
