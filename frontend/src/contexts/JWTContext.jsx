import React from 'react';
import AuthContext, { AuthProvider, useAuth } from './AuthContext';

/**
 * Legacy compatibility shim.
 *
 * Web runtime is now session-only and uses AuthContext as the single source of truth.
 * This file remains only to avoid breaking old imports that still reference JWTContext.
 */

export const JWTProvider = ({ children }) => <AuthProvider>{children}</AuthProvider>;

export default AuthContext;
export { useAuth };
