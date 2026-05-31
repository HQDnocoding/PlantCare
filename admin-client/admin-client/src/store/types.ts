// ─── Auth Service: User ───────────────────────────────────────────────────────
// Source: auth/entity/User.java
export type UserRole = 'FARMER' | 'ADMIN';
export type UserStatus = 'UNVERIFIED' | 'ACTIVE' | 'BLOCKED';

export interface User {
    id: string;             // UUID
    email: string;
    phone?: string;
    fullName: string;
    avatarUrl?: string;
    role: UserRole;
    status: UserStatus;
    createdAt: string;      // OffsetDateTime
    updatedAt: string;
}

// ─── User Service: UserProfile ────────────────────────────────────────────────
// Source: user-service/entity/UserProfile.java
export interface UserProfile {
    id: string;             // UUID (khác với User.id)
    userId: string;         // FK → auth.User.id
    displayName: string;
    bio?: string;
    avatarUrl?: string;
    avatarPath?: string;    // Firebase Storage path
    isDeleted: boolean;
    createdAt: string;
    updatedAt: string;
}

// ─── Community Service: Post ──────────────────────────────────────────────────
// Source: community-service/entity/Post.java
export interface Post {
    id: string;             // UUID
    authorId: string;       // UUID
    content: string;
    imageUrls: string[];    // TEXT[] array
    imagePaths: string[];   // Firebase paths
    upvoteCount: number;
    downvoteCount: number;
    commentCount: number;
    tags: string[];         // Set<String>
    isDeleted: boolean;
    deletedAt?: string;
    createdAt: string;
    updatedAt: string;
}

// ─── Community Service: Comment ───────────────────────────────────────────────
// Source: community-service/entity/Comment.java
export interface Comment {
    id: string;
    postId: string;
    authorId: string;
    parentId?: string;      // null = top-level, not null = reply (1 level only)
    content: string;
    upvoteCount: number;
    downvoteCount: number;
    replyCount: number;
    isDeleted: boolean;
    createdAt: string;
    updatedAt: string;
}

// ─── Scan Service: ScanHistory ────────────────────────────────────────────────
// Source: scan-service/entity/ScanHistory.java
export interface ScanHistory {
    id: string;             // UUID
    userId: string;
    imageUrl: string;
    disease: string;        // tên bệnh dạng string (không phải FK)
    confidence: number;     // BigDecimal 0..1
    confidentEnough: boolean;
    convId?: string;        // AI chat conversation ID
    scannedAt: string;      // LocalDateTime
}

// ─── AI Chat: Disease ─────────────────────────────────────────────────────────
// Source: ai-chat/app/models/disease.py
export interface Disease {
    id: number;
    order: number;
    className: string;      // unique key, vd: "XiMu"
    name: string;           // vd: "Bệnh xì mủ"
    description: string;
    symptoms: string[];     // JSON array
    cause: string;
    favorableConditions: string;
    treatment: string;
    prevention: string;
    medicines?: Medicine[]; // many-to-many
    version: string;
    createdAt: string;
    updatedAt: string;
}

// ─── AI Chat: Medicine ────────────────────────────────────────────────────────
// Source: ai-chat/app/models/disease.py
export interface Medicine {
    id: number;
    name: string;
    activeIngredient: string;
    formulation: string;    // vd: "Nhũ dầu EC", "Bột thấm nước WP"
    usage: string;
    dosage: string;
    weatherCondition: string;
    toxicity: string;
    safetyWarnings: string[]; // JSON array
    preHarvestInterval: string;
    diseases?: Disease[];   // many-to-many
    createdAt: string;
    updatedAt: string;
}

// ─── Notification ─────────────────────────────────────────────────────────────
// Source: notification-service/entity/Notification.java
export type NotificationType = 'FOLLOW' | 'COMMENT' | 'VOTE' | 'REPLY';

export interface Notification {
    id: string;
    userId: string;
    type: NotificationType;
    title: string;
    body: string;
    actorId?: string;
    actorName?: string;
    targetId?: string;
    isRead: boolean;
    createdAt: string;
}