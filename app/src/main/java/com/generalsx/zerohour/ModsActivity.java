/*
**	Command & Conquer Generals Zero Hour(tm)
**	Copyright 2025 Electronic Arts Inc.
**
**	This program is free software: you can redistribute it and/or modify
**	it under the terms of the GNU General Public License as published by
**	the Free Software Foundation, either version 3 of the License, or
**	(at your option) any later version.
**
**	This program is distributed in the hope that it will be useful,
**	but WITHOUT ANY WARRANTY; without even the implied warranty of
**	MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
**	GNU General Public License for more details.
**
**	You should have received a copy of the GNU General Public License
**	along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

// GeneralsX @feature Android port 30/10/2026 Mods screen.
//
// Lists the .big mod files the player has dropped into
// /sdcard/GeneralsMods (created on first open), lets them tick the ones they
// want, and applies them with one button. Applying is a placeholder for now:
// it shows a Toast and touches nothing on disk, because enabling a .big
// archive means editing the game's INI/engine load order (a native-side
// change), not copying a file.
//
// Storage reality on modern Android, which is why this screen asks for access
// itself instead of relying on the manifest alone:
//   * Android 9/10  -> READ/WRITE_EXTERNAL_STORAGE runtime grant.
//   * Android 11+   -> those two permissions are ignored by the OS. Listing
//                      an arbitrary folder on shared storage needs
//                      MANAGE_EXTERNAL_STORAGE ("All files access"), which is
//                      declared in the manifest and toggled by the user in
//                      system Settings -- there is no runtime dialog for it,
//                      so this screen deep-links straight to the right page
//                      and re-checks on resume.

package com.generalsx.zerohour;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class ModsActivity extends Activity {

    private static final int REQ_STORAGE = 41;
    private static final String MODS_FOLDER = "GeneralsMods";
    private static final String EXTENSION = ".big";

    private File modsDir;
    private ListView list;
    private TextView emptyView;
    private TextView countView;
    private TextView accessView;
    private View grantButton;
    private ModAdapter adapter;

    // Selection survives a refresh / a trip to the Settings screen, keyed by
    // file name: the File objects themselves are re-created on every reload.
    private final Set<String> selected = new HashSet<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(R.string.mods_title);

        // Environment.getExternalStorageDirectory() is /storage/emulated/0,
        // i.e. what the user sees as /sdcard -- the same root
        // FolderPickerActivity starts the game-folder browser in.
        modsDir = new File(Environment.getExternalStorageDirectory(), MODS_FOLDER);

        buildUi();
        refreshAccessState();
        if (hasStorageAccess()) {
            ensureFolder();
            reload();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // The user may have just come back from Settings after granting
        // all-files access, or dropped files in from a file manager.
        refreshAccessState();
        if (hasStorageAccess()) {
            ensureFolder();
            reload();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode != REQ_STORAGE) {
            return;
        }
        refreshAccessState();
        if (hasStorageAccess()) {
            ensureFolder();
            reload();
        }
    }

    // ------------------------------------------------------------- storage

    private boolean hasStorageAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // "All files access" -- the only thing that lets an app list a
            // folder it did not create on Android 11+.
            return Environment.isExternalStorageManager();
        }
        return checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
            == PackageManager.PERMISSION_GRANTED;
    }

    private void requestStorageAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // There is no runtime dialog for MANAGE_EXTERNAL_STORAGE; the
            // documented path is this Settings page, scoped to our package.
            try {
                startActivity(new Intent(
                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:" + getPackageName())));
            } catch (ActivityNotFoundException e) {
                // A few OEM builds only expose the unscoped page.
                startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
            }
        } else {
            requestPermissions(new String[] {
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            }, REQ_STORAGE);
        }
    }

    /** Create /sdcard/GeneralsMods if it is missing. Failure is not fatal. */
    private void ensureFolder() {
        if (!modsDir.isDirectory() && !modsDir.mkdirs()) {
            // Happens when the grant was just revoked, or on a device where
            // the card is read-only -- the empty state explains it.
            return;
        }
    }

    private void refreshAccessState() {
        final boolean ok = hasStorageAccess();
        accessView.setVisibility(ok ? View.GONE : View.VISIBLE);
        grantButton.setVisibility(ok ? View.GONE : View.VISIBLE);
        list.setVisibility(ok ? View.VISIBLE : View.GONE);
        emptyView.setVisibility(ok && adapter.getCount() == 0 ? View.VISIBLE : View.GONE);
        if (ok) {
            reload();
        }
    }

    // ----------------------------------------------------------------- ui

    private void buildUi() {
        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setBackgroundColor(UiKit.color(this, R.color.gzh_background));
        InsetUtil.applySafeInsets(shell);
        setContentView(shell);

        shell.addView(buildTopBar(), new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        shell.addView(buildFolderBar(), new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        FrameLayout listHost = new FrameLayout(this);
        LinearLayout.LayoutParams hostLp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        hostLp.setMargins(UiKit.dp(this, 12), UiKit.dp(this, 8),
            UiKit.dp(this, 12), UiKit.dp(this, 8));
        shell.addView(listHost, hostLp);

        list = new ListView(this);
        list.setDivider(new ColorDrawable(UiKit.color(this, R.color.gzh_outline_variant)));
        list.setDividerHeight(UiKit.dp(this, 1));
        list.setSelector(new ColorDrawable(Color.TRANSPARENT));
        list.setCacheColorHint(Color.TRANSPARENT);
        list.setVerticalScrollBarEnabled(true);
        adapter = new ModAdapter();
        list.setAdapter(adapter);
        listHost.addView(list, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        emptyView = new TextView(this);
        emptyView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        emptyView.setTextColor(UiKit.color(this, R.color.gzh_on_surface_variant));
        emptyView.setGravity(Gravity.CENTER);
        emptyView.setText(R.string.mods_empty);
        emptyView.setPadding(UiKit.dp(this, 24), UiKit.dp(this, 24),
            UiKit.dp(this, 24), UiKit.dp(this, 24));
        listHost.addView(emptyView, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // Only shown when the OS has not granted folder access: a big,
        // obvious way back to the permission page instead of a list that
        // silently stays empty.
        accessView = new TextView(this);
        accessView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        accessView.setTextColor(UiKit.color(this, R.color.gzh_status_warn));
        accessView.setGravity(Gravity.CENTER);
        accessView.setText(R.string.mods_need_access);
        accessView.setPadding(UiKit.dp(this, 24), UiKit.dp(this, 24),
            UiKit.dp(this, 24), UiKit.dp(this, 8));
        listHost.addView(accessView, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER));

        shell.addView(buildFooter(), new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private View buildTopBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(UiKit.dp(this, 20), UiKit.dp(this, 16),
            UiKit.dp(this, 12), UiKit.dp(this, 12));

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);

        TextView title = new TextView(this);
        title.setText(R.string.mods_title);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(UiKit.color(this, R.color.gzh_on_surface));
        titles.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText(R.string.mods_subtitle);
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        subtitle.setTextColor(UiKit.color(this, R.color.gzh_on_surface_faint));
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subLp.topMargin = UiKit.dp(this, 2);
        titles.addView(subtitle, subLp);

        bar.addView(titles, new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView close = new TextView(this);
        close.setText(R.string.mods_close);
        close.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        close.setTypeface(Typeface.DEFAULT_BOLD);
        close.setLetterSpacing(0.1f);
        close.setTextColor(UiKit.color(this, R.color.gzh_on_surface));
        close.setGravity(Gravity.CENTER);
        close.setPadding(UiKit.dp(this, 18), UiKit.dp(this, 10),
            UiKit.dp(this, 18), UiKit.dp(this, 10));
        GradientDrawable closeBg = new GradientDrawable();
        closeBg.setShape(GradientDrawable.RECTANGLE);
        closeBg.setCornerRadius(UiKit.dp(this, 10));
        closeBg.setColor(UiKit.color(this, R.color.gzh_surface_container_high));
        closeBg.setStroke(UiKit.dp(this, 1), UiKit.color(this, R.color.gzh_outline));
        close.setBackground(new RippleDrawable(
            UiKit.tint(this, R.color.gzh_ripple_light), closeBg, null));
        close.setClickable(true);
        close.setFocusable(true);
        close.setOnClickListener(v -> finish());
        bar.addView(close);

        return bar;
    }

    private View buildFolderBar() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(UiKit.dp(this, 16), UiKit.dp(this, 14),
            UiKit.dp(this, 16), UiKit.dp(this, 14));
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(UiKit.dp(this, 14));
        bg.setColor(UiKit.color(this, R.color.gzh_surface_container));
        card.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(UiKit.dp(this, 12), UiKit.dp(this, 4),
            UiKit.dp(this, 12), UiKit.dp(this, 4));
        card.setLayoutParams(lp);

        TextView path = new TextView(this);
        path.setText(modsDir.getAbsolutePath());
        path.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        path.setTextColor(UiKit.color(this, R.color.gzh_primary));
        card.addView(path);

        TextView hint = new TextView(this);
        hint.setText(R.string.mods_folder_hint);
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        hint.setTextColor(UiKit.color(this, R.color.gzh_on_surface_variant));
        LinearLayout.LayoutParams hintLp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hintLp.topMargin = UiKit.dp(this, 4);
        card.addView(hint, hintLp);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams actionsLp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        actionsLp.topMargin = UiKit.dp(this, 10);

        grantButton = smallAction(R.string.mods_grant, R.color.gzh_primary);
        grantButton.setOnClickListener(v -> requestStorageAccess());
        actions.addView(grantButton);

        View refresh = smallAction(R.string.mods_refresh, R.color.gzh_on_surface_variant);
        refresh.setOnClickListener(v -> {
            if (hasStorageAccess()) {
                ensureFolder();
                reload();
                Toast.makeText(this, R.string.mods_refreshed, Toast.LENGTH_SHORT).show();
            } else {
                requestStorageAccess();
            }
        });
        LinearLayout.LayoutParams refreshLp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        refreshLp.setMarginStart(UiKit.dp(this, 8));
        actions.addView(refresh, refreshLp);

        card.addView(actions, actionsLp);
        return card;
    }

    /** Small bordered text action used for CLOSE-style secondary buttons. */
    private View smallAction(int labelRes, int textColorRes) {
        TextView view = new TextView(this);
        view.setText(labelRes);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        view.setTypeface(Typeface.DEFAULT_BOLD);
        view.setLetterSpacing(0.08f);
        view.setTextColor(UiKit.color(this, textColorRes));
        view.setGravity(Gravity.CENTER);
        view.setPadding(UiKit.dp(this, 14), UiKit.dp(this, 8),
            UiKit.dp(this, 14), UiKit.dp(this, 8));
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(UiKit.dp(this, 8));
        bg.setColor(UiKit.color(this, R.color.gzh_surface_container_high));
        bg.setStroke(UiKit.dp(this, 1), UiKit.color(this, R.color.gzh_outline));
        view.setBackground(new RippleDrawable(
            UiKit.tint(this, R.color.gzh_ripple_light), bg, null));
        view.setClickable(true);
        view.setFocusable(true);
        return view;
    }

    private View buildFooter() {
        LinearLayout footer = new LinearLayout(this);
        footer.setOrientation(LinearLayout.VERTICAL);
        footer.setPadding(UiKit.dp(this, 12), UiKit.dp(this, 4),
            UiKit.dp(this, 12), UiKit.dp(this, 16));

        countView = new TextView(this);
        countView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        countView.setTextColor(UiKit.color(this, R.color.gzh_on_surface_variant));
        countView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams countLp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        countLp.bottomMargin = UiKit.dp(this, 10);
        footer.addView(countView, countLp);

        // APPLY MODS: gold (#FFD700) on blood red. The fill is the deep
        // primary container rather than the bright accent on purpose -- gold
        // on #E5484D is 2.8:1 and fails AA, gold on #5C1618 is 9.6:1, and the
        // accent colour is kept as the 2dp outline so it still reads "red".
        TextView apply = new TextView(this);
        apply.setText(R.string.mods_apply);
        apply.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f);
        apply.setTypeface(Typeface.DEFAULT_BOLD);
        apply.setLetterSpacing(0.08f);
        apply.setTextColor(0xFFFFD700);
        apply.setGravity(Gravity.CENTER);
        apply.setMinHeight(UiKit.dp(this, 56));
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(UiKit.dp(this, 14));
        bg.setColor(UiKit.color(this, R.color.gzh_primary_container));
        bg.setStroke(UiKit.dp(this, 2), UiKit.color(this, R.color.gzh_primary));
        apply.setBackground(new RippleDrawable(
            ColorStateList.valueOf(0x33FFD700), bg, null));
        apply.setClickable(true);
        apply.setFocusable(true);
        apply.setOnClickListener(v -> Toast.makeText(this,
            R.string.mods_apply_placeholder, Toast.LENGTH_SHORT).show());
        footer.addView(apply, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        return footer;
    }

    // --------------------------------------------------------------- model

    /** One .big file on disk plus the size/progress-free display bits. */
    private static final class ModEntry {
        final File file;
        final String name;
        final long size;

        ModEntry(File file) {
            this.file = file;
            this.name = file.getName();
            this.size = file.length();
        }
    }

    private void reload() {
        List<ModEntry> entries = new ArrayList<>();
        File[] files = modsDir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isFile() && f.getName().toLowerCase(Locale.ROOT).endsWith(EXTENSION)) {
                    entries.add(new ModEntry(f));
                }
            }
        }
        Collections.sort(entries, new Comparator<ModEntry>() {
            @Override
            public int compare(ModEntry a, ModEntry b) {
                return a.name.compareToIgnoreCase(b.name);
            }
        });

        // Forget selections whose file disappeared since the last reload, so
        // the count never claims a mod that is not on screen.
        Set<String> present = new HashSet<>();
        for (ModEntry e : entries) {
            present.add(e.name);
        }
        selected.retainAll(present);

        adapter.setItems(entries);
        emptyView.setVisibility(entries.isEmpty() ? View.VISIBLE : View.GONE);
        updateCount();
    }

    private void updateCount() {
        countView.setText(getString(R.string.mods_selected_count,
            selected.size(), adapter.getCount()));
    }

    private static String humanSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        double kb = bytes / 1024.0;
        if (kb < 1024) {
            return String.format(Locale.ROOT, "%.0f KB", kb);
        }
        double mb = kb / 1024.0;
        if (mb < 1024) {
            return String.format(Locale.ROOT, "%.1f MB", mb);
        }
        return String.format(Locale.ROOT, "%.2f GB", mb / 1024.0);
    }

    // ------------------------------------------------------------- adapter

    private final class ModAdapter extends BaseAdapter {

        private final List<ModEntry> items = new ArrayList<>();

        void setItems(List<ModEntry> newItems) {
            items.clear();
            items.addAll(newItems);
            notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            return items.size();
        }

        @Override
        public ModEntry getItem(int position) {
            return items.get(position);
        }

        @Override
        public long getItemId(int position) {
            return items.get(position).name.hashCode();
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ModRow row = convertView instanceof ModRow ? (ModRow) convertView : new ModRow();
            final ModEntry entry = getItem(position);
            row.bind(entry, selected.contains(entry.name));
            return row.root;
        }

        /**
         * One row: a checkbox carrying the mod's name, the file size pinned to
         * the end edge, and a whole-row tap that toggles the same checkbox.
         */
        private final class ModRow {
            final LinearLayout root;
            final CheckBox check;
            final TextView size;

            ModRow() {
                root = new LinearLayout(ModsActivity.this);
                root.setOrientation(LinearLayout.HORIZONTAL);
                root.setGravity(Gravity.CENTER_VERTICAL);
                root.setPadding(UiKit.dp(ModsActivity.this, 16), UiKit.dp(ModsActivity.this, 12),
                    UiKit.dp(ModsActivity.this, 16), UiKit.dp(ModsActivity.this, 12));
                root.setBackgroundColor(UiKit.color(ModsActivity.this, R.color.gzh_surface));

                check = new CheckBox(ModsActivity.this);
                check.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
                check.setTextColor(UiKit.color(ModsActivity.this, R.color.gzh_on_surface));
                check.setButtonTintList(new ColorStateList(
                    new int[][] { new int[] { android.R.attr.state_checked }, new int[0] },
                    new int[] { UiKit.color(ModsActivity.this, R.color.gzh_primary),
                                UiKit.color(ModsActivity.this, R.color.gzh_outline) }));
                check.setPadding(0, 0, 0, 0);
                root.addView(check, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

                size = new TextView(ModsActivity.this);
                size.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
                size.setTextColor(UiKit.color(ModsActivity.this, R.color.gzh_on_surface_faint));
                size.setGravity(Gravity.END);
                LinearLayout.LayoutParams sizeLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                sizeLp.setMarginStart(UiKit.dp(ModsActivity.this, 12));
                root.addView(size, sizeLp);

                root.setClickable(true);
            }

            void bind(final ModEntry entry, boolean isSelected) {
                check.setText(entry.name);
                // Rebind order matters: rows are recycled, so the listener has
                // to be detached before setChecked() -- otherwise restoring a
                // row's state would fire it and re-notify the count for a
                // different file than the one being displayed.
                check.setOnCheckedChangeListener(null);
                check.setChecked(isSelected);
                size.setText(humanSize(entry.size));
                check.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    if (isChecked) {
                        selected.add(entry.name);
                    } else {
                        selected.remove(entry.name);
                    }
                    updateCount();
                });
                // The whole row is a bigger tap target than the box. toggle()
                // goes through the change listener above, so the selection set
                // stays in sync either way.
                root.setOnClickListener(v -> check.toggle());
            }
        }
    }
}
