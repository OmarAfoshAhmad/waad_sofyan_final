import { useState } from 'react';
import { Button } from '@mui/material';
import { Upload } from '@mui/icons-material';
import ExcelImportDialog from './ExcelImportDialog';
import * as excelImportService from 'services/api/excel-import.service';

/**
 * Reusable Excel Import Button with Dialog
 *
 * Two usage modes:
 *
 * 1. Auto mode with module name (recommended):
 * <ExcelImportButton
 *   module="members"
 *   onImportComplete={() => refreshTable()}
 * />
 *
 * 2. Manual mode with custom functions:
 * <ExcelImportButton
 *   title="استيراد الأعضاء"
 *   templateFilename="Members_Template.xlsx"
 *   onDownloadTemplate={downloadMemberTemplate}
 *   onImport={importMembers}
 *   onSuccess={() => fetchMembers()}
 * />
 */
const ExcelImportButton = ({
  module, // Auto mode: 'members', 'providers', 'medical-services', etc.
  title,
  buttonLabel = 'استيراد من Excel',
  buttonVariant = 'outlined',
  buttonColor = 'primary',
  templateFilename,
  onDownloadTemplate,
  onImport,
  onSuccess,
  onImportComplete, // Alias for onSuccess in auto mode
  ...buttonProps
}) => {
  const [dialogOpen, setDialogOpen] = useState(false);

  // Auto-detect functions based on module
  const getModuleFunctions = () => {
    if (!module) {
      return { downloadFn: onDownloadTemplate, importFn: onImport };
    }

    switch (module) {
      case 'members':
        return {
          downloadFn: excelImportService.downloadMemberTemplate,
          importFn: excelImportService.importMembers,
          title: 'استيراد الأعضاء',
          filename: 'Members_Import_Template.xlsx'
        };
      case 'providers':
        return {
          downloadFn: excelImportService.downloadProviderTemplate,
          importFn: excelImportService.importProviders,
          title: 'استيراد مقدمي الخدمة',
          filename: 'Providers_Import_Template.xlsx'
        };
      case 'medical-services':
        return {
          downloadFn: excelImportService.downloadMedicalServiceTemplate,
          importFn: excelImportService.importMedicalServices,
          title: 'استيراد الخدمات الطبية',
          filename: 'Medical_Services_Import_Template.xlsx'
        };
      case 'medical-categories':
        return {
          downloadFn: excelImportService.downloadMedicalCategoryTemplate,
          importFn: excelImportService.importMedicalCategories,
          title: 'استيراد التصنيفات الطبية',
          filename: 'Medical_Categories_Import_Template.xlsx'
        };
      default:
        console.error(`[ExcelImportButton] Unknown module: ${module}`);
        return { downloadFn: onDownloadTemplate, importFn: onImport };
    }
  };

  const moduleFunctions = getModuleFunctions();
  const finalTitle = title || moduleFunctions.title || 'استيراد من Excel';
  const finalFilename = templateFilename || moduleFunctions.filename;
  const finalDownloadFn = moduleFunctions.downloadFn;
  const finalImportFn = moduleFunctions.importFn;
  const finalSuccessFn = onSuccess || onImportComplete;

  const handleDialogClose = () => {
    setDialogOpen(false);
  };

  const handleImport = async (file) => {
    const result = await finalImportFn(file);

    // Call success callback if provided and import was successful
    // Backend returns 'inserted' and 'updated', not 'created'
    const successCount = (result?.summary?.inserted || 0) + (result?.summary?.updated || 0) + (result?.summary?.created || 0);
    if (finalSuccessFn && result.success && successCount > 0) {
      finalSuccessFn(result);
    }

    return result;
  };

  return (
    <>
      <Button variant={buttonVariant} color={buttonColor} startIcon={<Upload />} onClick={() => setDialogOpen(true)} {...buttonProps}>
        {buttonLabel}
      </Button>

      <ExcelImportDialog
        open={dialogOpen}
        onClose={handleDialogClose}
        title={finalTitle}
        templateFilename={finalFilename}
        onDownloadTemplate={finalDownloadFn}
        onImport={handleImport}
      />
    </>
  );
};

export default ExcelImportButton;
