/**
 * Export data to PDF file
 * Simple implementation using browser print
 * @param {Array<string>} columns - Column headers
 * @param {Array<Array>} rows - Row data
 * @param {string} title - Document title
 * @param {string} filename - Filename without extension
 * @param {Object} options - Export options
 * @param {string} options.companyName - Company name for header (from CompanySettingsContext)
 * @param {string} options.primaryColor - Brand color for styling
 * @param {string} options.logoBase64 - Base64 encoded logo image
 */
export const exportToPDF = (columns, rows, title = 'Export', filename = 'export', options = {}) => {
  if (!rows || rows.length === 0) {
    console.warn('No data to export');
    return;
  }

  const { companyName, primaryColor = '#4a4a4a', logoBase64 } = options;

  // Build company header HTML
  const companyHeader = companyName
    ? `
    <div class="company-header">
      ${logoBase64 ? `<img src="${logoBase64}" alt="${companyName}" class="company-logo" />` : ''}
      <div class="company-name">${companyName}</div>
    </div>
  `
    : '';

  // Create printable HTML table
  const tableHTML = `
    <!DOCTYPE html>
    <html dir="rtl" lang="ar">
    <head>
      <meta charset="UTF-8">
      <title>${title}</title>
      <style>
        * {
          box-sizing: border-box;
        }
        body {
          font-family: 'Segoe UI', Tahoma, Arial, sans-serif;
          margin: 20px;
          direction: rtl;
        }
        .company-header {
          display: flex;
          align-items: center;
          justify-content: center;
          gap: 15px;
          margin-bottom: 20px;
          padding-bottom: 15px;
          border-bottom: 2px solid ${primaryColor};
        }
        .company-logo {
          max-height: 50px;
          max-width: 150px;
          object-fit: contain;
        }
        .company-name {
          font-size: 24px;
          font-weight: bold;
          color: ${primaryColor};
        }
        h1 {
          text-align: center;
          color: #333;
          margin-bottom: 20px;
        }
        table {
          width: '6.25rem'%;
          border-collapse: collapse;
          margin-bottom: 20px;
        }
        th, td {
          border: 1px solid #ddd;
          padding: 10px 8px;
          text-align: right;
        }
        th {
          background-color: ${primaryColor};
          color: white;
          font-weight: bold;
        }
        tr:nth-child(even) {
          background-color: #f9f9f9;
        }
        tr:hover {
          background-color: #f5f5f5;
        }
        .footer {
          text-align: center;
          color: #666;
          font-size: 12px;
          margin-top: 20px;
        }
        @media print {
          body { margin: 0; }
          .no-print { display: none; }
        }
      </style>
    </head>
    <body>
      ${companyHeader}
      <h1>${title}</h1>
      <table>
        <thead>
          <tr>
            ${columns.map((col) => `<th>${col}</th>`).join('')}
          </tr>
        </thead>
        <tbody>
          ${rows
            .map(
              (row) => `
            <tr>
              ${row.map((cell) => `<td>${cell ?? '-'}</td>`).join('')}
            </tr>
          `
            )
            .join('')}
        </tbody>
      </table>
      <div class="footer">
        ${companyName ? `${companyName} | ` : ''}تم الإنشاء: ${new Date().toLocaleDateString('en-US')} ${new Date().toLocaleTimeString('en-US')}
      </div>
    </body>
    </html>
  `;

  // Open print window
  const printWindow = window.open('', '_blank');
  if (printWindow) {
    printWindow.document.write(tableHTML);
    printWindow.document.close();
    printWindow.focus();

    // Auto print after a short delay
    setTimeout(() => {
      printWindow.print();
    }, 250);
  } else {
    console.error('Could not open print window. Please check popup blocker settings.');
  }
};

export default exportToPDF;
