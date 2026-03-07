/**
 * Provider Contracts Module - Index/Entry Point
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * This file serves as the default export for the provider-contracts route.
 * It re-exports the ProviderContractsList component.
 *
 * Module Structure:
 * - index.jsx (this file) - Route entry point
 * - ProviderContractsList.jsx - List view with table
 * - ProviderContractView.jsx - Detail view with pricing table
 * - data/providerContracts.mock.js - Mock data for development
 *
 * Backend Status: IMPLEMENTED
 * This module uses the real provider-contracts API via provider-contracts.service.js
 * Backend endpoint: /api/v1/provider-contracts (ProviderContractController.java)
 *
 * Route Configuration (in MainRoutes.jsx):
 * - /provider-contracts → ProviderContractsList (this export)
 * - /provider-contracts/:id → ProviderContractView
 *
 * @version 1.0.0
 * @lastUpdated 2024-12-24
 */

// Re-export the list component as default
export { default } from './ProviderContractsList';

// Named exports for explicit imports
export { default as ProviderContractsList } from './ProviderContractsList';
export { default as ProviderContractView } from './ProviderContractView';
