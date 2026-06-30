import axios from 'utils/axios';

export const runCoverageSimulation = async (contractId, policyId, encounterType = 'ALL', saveSnapshot = true) => {
  const response = await axios.post('/simulation/coverage/contract', {
    contractId,
    policyId,
    encounterType,
    saveSnapshot,
  }, {
    timeout: 300000 // 5 minutes timeout for large contract simulations
  });
  return response.data;
};

export const runRawCoverageSimulation = async (policyId, encounterType = 'ALL', items, saveSnapshot = true) => {
  const response = await axios.post('/simulation/coverage/raw', items, {
    params: {
      policyId,
      encounterType
    },
    timeout: 300000 // 5 minutes timeout for large files (4000+ items)
  });
  return response.data;
};

export const downloadSimulationReport = async (simulationId) => {
  const response = await axios.get(`/simulation/coverage/export/${simulationId}`, {
    responseType: 'blob',
  });
  return response.data;
};

export const compareSimulations = async (baseId, compareId) => {
  const response = await axios.get('/simulation/coverage/compare', {
    params: { baseId, compareId }
  });
  return response.data;
};

const simulationService = {
  runCoverageSimulation,
  runRawCoverageSimulation,
  downloadSimulationReport,
  compareSimulations,
};

export default simulationService;
