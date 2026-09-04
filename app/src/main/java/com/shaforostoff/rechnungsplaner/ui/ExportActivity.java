package com.shaforostoff.rechnungsplaner.ui;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;

import com.shaforostoff.rechnungsplaner.MainActivity;
import com.shaforostoff.rechnungsplaner.R;
import com.shaforostoff.rechnungsplaner.data.GigDao;
import com.shaforostoff.rechnungsplaner.data.SettingsStore;
import com.shaforostoff.rechnungsplaner.exchange.Backup;
import com.shaforostoff.rechnungsplaner.exchange.ContactsArchive;
import com.shaforostoff.rechnungsplaner.exchange.GigTextExporter;
import com.shaforostoff.rechnungsplaner.output.SafExporter;
import com.shaforostoff.rechnungsplaner.output.Sharing;
import com.shaforostoff.rechnungsplaner.util.ShareProvider;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * The full backup, the two bulk exports, and the way in from the device calendar.
 *
 * <p>Each section is one body of data with every direction it travels in on it: the backup is
 * written and restored in one place, and the contacts archive is exported and imported in one
 * place, because splitting a round trip across two sections asks the reader to notice that two
 * headings are about the same file.
 *
 * <p>The backup sits at the top because it is the one action here that matters when something has
 * gone wrong. Its restore replaces what is on the phone, where the contacts import merges into it
 * -- which is the difference the two captions have to carry, since the buttons look alike.
 */
public class ExportActivity extends BaseActivity {

    private static final int REQUEST_PICK_IMPORT = 61;
    private static final int REQUEST_PICK_RESTORE = 62;
    private static final int REQUEST_PICK_FOLDER = 63;

    private SettingsStore settings;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        settings = new SettingsStore(this);
        setScreenTitle(R.string.title_import_export);

        FormBuilder f = form();

        f.section(R.string.backup_full);
        f.caption(getString(R.string.backup_full_desc));
        f.primaryButton(R.string.action_save_to_folder, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveBackup();
            }
        });
        f.secondaryButton(R.string.action_share, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                shareBackup();
            }
        });
        f.secondaryButton(R.string.action_restore, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pickRestoreFile();
            }
        });

        f.section(R.string.export_tour_list);
        f.caption(getString(R.string.export_tour_list_desc));
        f.primaryButton(R.string.action_share, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                shareTourList();
            }
        });
        f.secondaryButton(R.string.action_copy, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                copyTourList();
            }
        });

        f.section(R.string.contacts_archive);
        f.caption(getString(R.string.contacts_archive_desc));
        f.primaryButton(R.string.action_export, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                exportContacts();
            }
        });
        f.secondaryButton(R.string.action_import, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pickImportFile();
            }
        });

        f.section(R.string.title_import_calendar);
        f.caption(getString(R.string.import_calendar_desc));
        f.secondaryButton(R.string.action_import, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(ExportActivity.this, CalendarImportActivity.class));
            }
        });
    }

    private String tourList() {
        return GigTextExporter.export(new GigDao(this).upcoming(), settings.getTourDateFormat());
    }

    private void shareTourList() {
        String text = tourList();
        if (text.isEmpty()) {
            Ui.toast(this, R.string.nothing_to_export);
            return;
        }
        startActivity(Sharing.shareText(text, getString(R.string.export_tour_list),
                getString(R.string.action_share)));
    }

    private void copyTourList() {
        String text = tourList();
        if (text.isEmpty()) {
            Ui.toast(this, R.string.nothing_to_export);
            return;
        }
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.export_tour_list), text));
        Ui.toast(this, R.string.copied_to_clipboard);
    }

    private void exportContacts() {
        try {
            ContactsArchive archive = new ContactsArchive(this);
            File file = archive.export(ShareProvider.shareDir(this),
                    settings.isStrictLexofficeExport());
            List<File> files = new ArrayList<File>();
            files.add(file);
            startActivity(Sharing.share(this, files, null,
                    getString(R.string.contacts_archive), null,
                    getString(R.string.action_share)));
        } catch (IOException e) {
            Ui.toast(this, getString(R.string.import_failed, String.valueOf(e.getMessage())));
        }
    }

    // ------------------------------------------------------------------- backup

    /** Writes the backup into the app's own directory first, whatever happens to it next. */
    private File writeBackup() {
        try {
            return new Backup(this).export(ShareProvider.shareDir(this));
        } catch (IOException e) {
            Ui.toast(this, getString(R.string.backup_failed, String.valueOf(e.getMessage())));
            return null;
        }
    }

    private void saveBackup() {
        String tree = settings.getExportTreeUri();
        if (tree == null) {
            Ui.toast(this, R.string.no_export_folder);
            startActivityForResult(SafExporter.pickFolderIntent(), REQUEST_PICK_FOLDER);
            return;
        }
        File file = writeBackup();
        if (file == null) return;
        try {
            if (SafExporter.copyInto(this, tree, file) == null) {
                Ui.toast(this, R.string.no_export_folder);
                return;
            }
            // The folder name is what the user recognises; the tree URI is not readable.
            String folder = SafExporter.displayName(this, tree);
            Ui.toast(this, folder == null ? getString(R.string.saved_to_folder_unnamed)
                    : getString(R.string.saved_to_folder, folder));
        } catch (IOException e) {
            Ui.toast(this, getString(R.string.backup_failed, String.valueOf(e.getMessage())));
        }
    }

    private void shareBackup() {
        File file = writeBackup();
        if (file == null) return;
        List<File> files = new ArrayList<File>();
        files.add(file);
        try {
            startActivity(Sharing.share(this, files, null, getString(R.string.backup_full), null,
                    getString(R.string.action_share)));
        } catch (IOException e) {
            Ui.toast(this, getString(R.string.backup_failed, String.valueOf(e.getMessage())));
        }
    }

    private void pickRestoreFile() {
        startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("*/*")
                .putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/zip"}),
                REQUEST_PICK_RESTORE);
    }

    private void restoreFrom(Uri uri) {
        final Backup backup = new Backup(this);
        final Backup.RestorePlan plan;
        try {
            plan = backup.plan(uri);
        } catch (IOException e) {
            Ui.toast(this, getString(R.string.import_failed, String.valueOf(e.getMessage())));
            return;
        }
        if (!plan.recognised) {
            Ui.toast(this, R.string.restore_not_a_backup);
            return;
        }

        StringBuilder message = new StringBuilder(getString(R.string.restore_summary,
                plan.document.createdAt == null ? "?" : plan.document.createdAt,
                plan.customers(), plan.gigs(), plan.invoices(), plan.files,
                plan.document.settings.size()));
        for (String warning : plan.document.warnings) message.append('\n').append(warning);
        message.append("\n\n").append(getString(R.string.restore_replaces_everything));

        Ui.confirm(this, message.toString(), R.string.action_restore, new Runnable() {
            @Override
            public void run() {
                restore(backup, plan);
            }
        });
    }

    private void restore(Backup backup, Backup.RestorePlan plan) {
        try {
            backup.apply(plan);
        } catch (IOException e) {
            Ui.toast(this, getString(R.string.restore_failed, String.valueOf(e.getMessage())));
            return;
        }
        Ui.toast(this, R.string.restore_done);
        // Every screen behind this one is showing data that has just been replaced, and the UI
        // language may have been replaced with it, so the task is rebuilt rather than returned to.
        startActivity(new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK));
        finish();
    }

    // ------------------------------------------------------------------- import

    private void pickImportFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("*/*")
                .putExtra(Intent.EXTRA_MIME_TYPES,
                        new String[]{"application/zip", "application/json", "text/plain"});
        startActivityForResult(intent, REQUEST_PICK_IMPORT);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (data == null || data.getData() == null) return;
        Uri uri = data.getData();
        switch (requestCode) {
            case REQUEST_PICK_IMPORT:
                importFrom(uri);
                break;
            case REQUEST_PICK_RESTORE:
                restoreFrom(uri);
                break;
            case REQUEST_PICK_FOLDER:
                SafExporter.persistPermission(this, uri);
                settings.setExportTreeUri(uri.toString());
                saveBackup();
                break;
            default:
                break;
        }
    }

    private void importFrom(Uri uri) {
        final ContactsArchive archive = new ContactsArchive(this);
        final ContactsArchive.ImportPlan plan;
        try {
            InputStream in = getContentResolver().openInputStream(uri);
            if (in == null) {
                Ui.toast(this, getString(R.string.import_failed, uri.toString()));
                return;
            }
            try {
                plan = archive.plan(in, nameOf(uri));
            } finally {
                in.close();
            }
        } catch (IOException e) {
            Ui.toast(this, getString(R.string.import_failed, String.valueOf(e.getMessage())));
            return;
        }

        int created = plan.countMatching(ContactsArchive.ImportPlan.Action.CREATE);
        int updated = plan.countMatching(ContactsArchive.ImportPlan.Action.UPDATE);
        StringBuilder message = new StringBuilder(getString(R.string.import_summary, created,
                updated));
        for (String warning : plan.warnings) message.append('\n').append(warning);

        // The issuer is the one thing an import can overwrite that the user cannot easily get back,
        // so it is asked about separately rather than folded into the summary.
        if (plan.replaceIssuer) message.append("\n\n").append(getString(
                R.string.import_replace_issuer));

        new AlertDialog.Builder(this)
                .setMessage(message.toString())
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_import, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        archive.apply(plan);
                        Ui.toast(ExportActivity.this, R.string.action_import);
                    }
                })
                .show();
    }

    private String nameOf(Uri uri) {
        String last = uri.getLastPathSegment();
        return last == null ? "import.json" : last;
    }
}
