/**
 * Unified Members Module - Index
 *
 * Exports all unified member components and pages.
 *
 * @module UnifiedMembers
 * @since 2026-01-11
 */

export { default as UnifiedMembersList } from './UnifiedMembersList';
export { default as UnifiedMemberCreate } from './UnifiedMemberCreate';
export { default as UnifiedMemberView } from './UnifiedMemberView';
export { default as UnifiedMemberEdit } from './UnifiedMemberEdit';
export { default as EligibilityCheck } from './EligibilityCheck';

// Backward-compatible aliases that point to the unified implementation.
export { default as MembersList } from './UnifiedMembersList';
export { default as MemberCreate } from './UnifiedMemberCreate';
export { default as MemberView } from './UnifiedMemberView';
export { default as MemberEdit } from './UnifiedMemberEdit';
