/**
 * Export data to Excel file
 * @param {Array<Object>} data - Array of objects to export
 * @param {string} filename - Filename without extension
 * @param {Object} options - Export options
 * @param {string} options.companyName - Company name for header row (from CompanySettingsContext)
 */
export const exportToExcel = (data, filename = 'export', options = {}) => {
  if (!data || data.length === 0) {
    console.warn('No data to export');
    return;
  }

  const { companyName } = options;

  // Get headers from first object
  const headers = Object.keys(data[0]);

  // Create CSV content with BOM for Arabic support
  const BOM = '\uFEFF';
  let csvContent = BOM;

  // Add company name header if provided
  if (companyName) {
    csvContent += `"${companyName}"\n`;
    csvContent += `"تاريخ التصدير: ${new Date().toLocaleDateString('en-GB')}"\n`;
    csvContent += '\n'; // Empty row for spacing
  }

  // Add headers
  csvContent += headers.map((h) => `"${h}"`).join(',') + '\n';

  // Add rows
  data.forEach((row) => {
    const rowData = headers.map((header) => {
      const value = row[header];
      // Handle null/undefined
      if (value === null || value === undefined) return '""';
      // Escape quotes and wrap in quotes
      return `"${String(value).replace(/"/g, '""')}"`;
    });
    csvContent += rowData.join(',') + '\n';
  });

  // Create blob and download
  const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
  const link = document.createElement('a');
  const url = URL.createObjectURL(blob);

  link.setAttribute('href', url);
  link.setAttribute('download', `${filename}.csv`);
  link.style.visibility = 'hidden';
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  URL.revokeObjectURL(url);
};

export default exportToExcel;
