"""
Authentication and authorization module for role-based access control.
Supports:
1. Firebase JWT tokens for user authentication
2. Custom JWT tokens for user authentication
3. Role-based access control (ADMIN role)

Aligns with admin-service JWT implementation:
- Uses HMAC-SHA512 key derivation (Base64 decode first, fallback to raw bytes)
- Supports custom claims including roles
- Falls back gracefully if Firebase is unavailable
"""

import jwt as pyjwt
import base64
from fastapi import HTTPException, Header, status
from typing import Optional, Dict, Any
from app.core.config import settings
import logging

logger = logging.getLogger(__name__)


class UserClaims:
    """Represents authenticated user claims extracted from JWT"""

    def __init__(
        self,
        uid: str,
        email: str,
        roles: list = None,
        custom_claims: Dict[str, Any] = None,
    ):
        self.uid = uid
        self.email = email
        self.roles = roles or []
        self.custom_claims = custom_claims or {}

    def has_role(self, role: str) -> bool:
        """Check if user has a specific role"""
        return role.upper() in [r.upper() for r in self.roles]

    def is_admin(self) -> bool:
        """Check if user has ADMIN role"""
        return self.has_role("ADMIN")


def decode_jwt_token(token: str, secret: str = None) -> Dict[str, Any]:
    """
    Decode and verify JWT token.
    Supports both Base64-encoded secrets and raw string secrets (compatible with Java implementation).

    Args:
        token: JWT token to decode
        secret: Secret key for verification (uses internal_secret from config if not provided)

    Returns:
        Decoded JWT payload

    Raises:
        HTTPException: If token is invalid or verification fails
    """
    if not secret:
        secret = settings.internal_secret or "default-secret"

    try:
        # Try to decode secret as Base64 first (Java compatible)
        try:
            key_bytes = base64.b64decode(secret)
        except Exception:
            key_bytes = secret.encode("utf-8")

        # Decode JWT token (support both HS256 and HS512 like Java)
        payload = pyjwt.decode(
            token,
            key_bytes,
            algorithms=["HS256", "HS512"],
            options={"verify_aud": False},
        )

        logger.debug(f"JWT token decoded successfully")
        return payload

    except pyjwt.ExpiredSignatureError:
        logger.warning("JWT token has expired")
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED, detail="Token has expired"
        )
    except pyjwt.InvalidTokenError as e:
        logger.error(f"JWT token verification failed: {str(e)}")
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid token"
        )
    except Exception as e:
        logger.error(f"Token verification error: {str(e)}")
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED, detail="Authentication failed"
        )


def verify_firebase_token_optional(token: str) -> Dict[str, Any]:
    """
    Try to verify token with Firebase.
    Falls back to JWT verification if Firebase is unavailable.

    Args:
        token: JWT token to verify

    Returns:
        Decoded token claims
    """
    try:
        # Try Firebase verification first
        import firebase_admin
        from firebase_admin import auth as firebase_auth

        if firebase_admin._apps:
            decoded_token = firebase_auth.verify_id_token(token)
            logger.info("Firebase token verified successfully")
            return decoded_token
    except Exception as e:
        logger.debug(f"Firebase verification failed: {str(e)}, falling back to JWT")

    # Fall back to JWT verification
    return decode_jwt_token(token)


def get_current_user(authorization: Optional[str] = Header(None)) -> UserClaims:
    """
    FastAPI dependency to get current authenticated user.
    Expects Authorization header with format: "Bearer <token>"

    Supports both Firebase JWT and custom JWT tokens.

    Args:
        authorization: Authorization header value

    Returns:
        UserClaims object with user information

    Raises:
        HTTPException: If token is missing or invalid
    """
    if not authorization:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Missing authorization header",
            headers={"WWW-Authenticate": "Bearer"},
        )

    # Extract token from "Bearer <token>"
    parts = authorization.split()
    if len(parts) != 2 or parts[0].lower() != "bearer":
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid authorization header format. Use: Bearer <token>",
            headers={"WWW-Authenticate": "Bearer"},
        )

    token = parts[1]

    # Try to verify token (Firebase first, then fallback to JWT)
    payload = verify_firebase_token_optional(token)

    # Extract user information from token
    # Support multiple claim names: uid, sub, subject (Firebase uses different names)
    uid = payload.get("uid") or payload.get("sub") or payload.get("subject")
    email = payload.get("email", "unknown")

    # Extract roles from custom claims
    roles = payload.get("roles", [])
    if isinstance(roles, str):
        roles = [roles]
    elif not isinstance(roles, list):
        roles = []

    logger.info(f"User {uid} ({email}) authenticated with roles: {roles}")

    return UserClaims(uid=uid, email=email, roles=roles, custom_claims=payload)


def get_admin_user(authorization: Optional[str] = Header(None)) -> UserClaims:
    """
    Dependency for admin-only endpoints.
    Verifies authentication AND admin role.

    Args:
        authorization: Authorization header with JWT token

    Returns:
        UserClaims if user is authenticated and has ADMIN role

    Raises:
        HTTPException: If authentication fails or user is not admin
    """
    user = get_current_user(authorization)

    if not user.is_admin():
        logger.warning(f"Admin access denied for user {user.uid} ({user.email})")
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="Admin role required for this operation",
        )

    return user
