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

import com.shaforostoff.rechnungsplaner.R;
import com.shaforostoff.rechnungsplaner.data.GigDao;
import com.shaforostoff.rechnungsplaner.data.SettingsStore;
import com.shaforostoff.rechnungsplaner.exchange.ContactsArchive;
import com.shaforostoff.rechnungsplaner.exchange.GigTextExporter;
import com.shaforostoff.rechnungsplaner.output.Sharing;
import com.shaforostoff.rechnungsplaner.util.ShareProvider;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/** The two bulk exports and the contacts import. */
public class ExportActivity extends BaseActivity {

    private static final int REQUEST_PICK_IMPORT = 61;

    private SettingsStore settings;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        settings = new SettingsStore(this);
        setScreenTitle(R.string.title_export);

        FormBuilder f = form();

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

        f.section(R.string.export_contacts);
        f.caption(getString(R.string.export_contacts_desc));
        f.primaryButton(R.string.action_export, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                exportContacts();
            }
        });

        f.section(R.string.import_contacts);
        f.caption(getString(R.string.import_contacts_desc));
        f.secondaryButton(R.string.action_import, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pickImportFile();
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
                    getString(R.string.export_contacts), null,
                    getString(R.string.action_share)));
        } catch (IOException e) {
            Ui.toast(this, getString(R.string.import_failed, String.valueOf(e.getMessage())));
        }
    }

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
        if (requestCode != REQUEST_PICK_IMPORT || data == null || data.getData() == null) return;
        importFrom(data.getData());
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
