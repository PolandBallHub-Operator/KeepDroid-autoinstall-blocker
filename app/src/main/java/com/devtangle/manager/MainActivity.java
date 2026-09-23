package com.devtangle.manager;

import android.Manifest;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.android.apksig.ApkSigner;
import com.apk.axml.aXMLDecoder;
import com.apk.axml.aXMLEncoder;
import com.apk.axml.serializableItems.XMLEntry;
import org.woheller69.freeDroidWarn.FreeDroidWarn;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.textfield.TextInputEditText;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.Enumeration;
import java.util.regex.Pattern;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public class MainActivity extends AppCompatActivity {
    private static final Pattern PACKAGE = Pattern.compile("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+$");
    private static final String DEFAULT_PASSWORD = "keepdroidmaker-debug";
    private static final int PICK_KEYSTORE = 41, REQUEST_STORAGE = 42;
    private TextInputEditText packageInput, appNameInput, passwordInput;
    private MaterialCheckBox termsCheck;
    private TextView result, keystoreStatus;
    private Uri selectedKeystore;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state); setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.top_app_bar), (view, insets) -> { Insets bars = insets.getInsets(WindowInsetsCompat.Type.statusBars()); android.view.ViewGroup.MarginLayoutParams p = (android.view.ViewGroup.MarginLayoutParams)view.getLayoutParams(); p.topMargin = bars.top; view.setLayoutParams(p); return insets; });
        packageInput = findViewById(R.id.package_input); appNameInput = findViewById(R.id.app_name_input); passwordInput = findViewById(R.id.password_input); termsCheck = findViewById(R.id.terms_check); result = findViewById(R.id.result); keystoreStatus = findViewById(R.id.keystore_status);
        ((MaterialButton)findViewById(R.id.keystore_button)).setOnClickListener(v -> pickKeystore());
        ((MaterialButton)findViewById(R.id.create_button)).setOnClickListener(v -> saveSpecification());
        ((MaterialButton)findViewById(R.id.export_button)).setOnClickListener(v -> startGeneration());
        ((MaterialButton)findViewById(R.id.install_button)).setOnClickListener(v -> chooseApk());
        FreeDroidWarn.showWarningOnUpgrade(this, BuildConfig.VERSION_CODE);
    }
    private String pkg() { return packageInput.getText() == null ? "" : packageInput.getText().toString().trim(); }
    private String appName() { return appNameInput.getText() == null ? "" : appNameInput.getText().toString().trim(); }
    private boolean validateInput() { if (!PACKAGE.matcher(pkg()).matches()) { packageInput.setError(getString(R.string.invalid_package)); return false; } if (appName().isEmpty()) { appNameInput.setError(getString(R.string.invalid_app_name)); return false; } if (!termsCheck.isChecked()) { Toast.makeText(this, R.string.agree_required, Toast.LENGTH_SHORT).show(); return false; } return true; }
    private void pickKeystore() { startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE), PICK_KEYSTORE); }
    @Override protected void onActivityResult(int request, int resultCode, Intent data) { super.onActivityResult(request, resultCode, data); if (request == PICK_KEYSTORE && resultCode == RESULT_OK && data != null) { selectedKeystore = data.getData(); try { getContentResolver().takePersistableUriPermission(selectedKeystore, Intent.FLAG_GRANT_READ_URI_PERMISSION); } catch (Exception ignored) {} keystoreStatus.setText(selectedKeystore.getLastPathSegment()); passwordInput.setText(""); } }
    private void saveSpecification() { if (!validateInput()) return; try { File out = new File(generatedDir(), pkg().replace('.', '_') + ".json"); String json = "{\n  \"packageName\": \"" + pkg() + "\",\n  \"appName\": \"" + appName() + "\",\n  \"deviceAdminRequested\": true\n}\n"; try (FileOutputStream stream = new FileOutputStream(out)) { stream.write(json.getBytes(StandardCharsets.UTF_8)); } result.setText(out.getAbsolutePath()); Toast.makeText(this, R.string.saved_toast, Toast.LENGTH_SHORT).show(); } catch (Exception e) { result.setText(e.toString()); } }
    private void startGeneration() { if (!validateInput()) return; if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) { requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_STORAGE); return; } generateAndInstall(); }
    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grants) { super.onRequestPermissionsResult(requestCode, permissions, grants); if (requestCode == REQUEST_STORAGE && grants.length > 0 && grants[0] == PackageManager.PERMISSION_GRANTED) generateAndInstall(); }
    private void generateAndInstall() { new Thread(() -> { try { Uri output = buildApk(pkg(), appName()); runOnUiThread(() -> { result.setText(getString(R.string.result_title) + "\n/sdcard/Documents/KeepDroid/apk\n\n" + getString(R.string.result_body)); openInstaller(output); }); } catch (Exception e) { runOnUiThread(() -> { result.setText(getString(R.string.keystore_error) + "\n" + e.getMessage()); Toast.makeText(this, R.string.keystore_error, Toast.LENGTH_LONG).show(); }); } }).start(); }
    private File generatedDir() { File dir = new File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "generated"); if (!dir.exists()) dir.mkdirs(); return dir; }
    private String password() { String p = passwordInput.getText() == null ? "" : passwordInput.getText().toString(); return p.isEmpty() && selectedKeystore == null ? DEFAULT_PASSWORD : p; }
    private File copySelectedKeystore() throws IOException { File out = new File(getCacheDir(), "selected.keystore"); try (InputStream in = getContentResolver().openInputStream(selectedKeystore); OutputStream stream = new FileOutputStream(out)) { if (in == null) throw new IOException("No keystore stream"); copy(in, stream); } return out; }
    private File copyAssetKey() throws IOException { File out = new File(getCacheDir(), "bundled.keystore"); try (InputStream in = getAssets().open("generator-debug.keystore"); OutputStream stream = new FileOutputStream(out)) { copy(in, stream); } return out; }
    private Uri buildApk(String pkg, String label) throws Exception {
        File dir = new File(getCacheDir(), "generated"); if (!dir.exists()) dir.mkdirs(); File unsigned = new File(dir, pkg.replace('.', '_') + "-unsigned.apk"); File signed = new File(dir, pkg.replace('.', '_') + ".apk"); File source = new File(getCacheDir(), "template.apk");
        try (InputStream in = getAssets().open("template.apk"); OutputStream out = new FileOutputStream(source)) { copy(in, out); }
        try (ZipFile zip = new ZipFile(source); ZipOutputStream out = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(unsigned)))) { Enumeration<? extends ZipEntry> entries = zip.entries(); while (entries.hasMoreElements()) { ZipEntry entry = entries.nextElement(); String name = entry.getName(); if (name.startsWith("META-INF/")) continue; byte[] replacement = null; if (name.equals("AndroidManifest.xml")) { try (InputStream in = zip.getInputStream(entry)) { java.util.List<XMLEntry> xml = new aXMLDecoder(in).decode(); for (XMLEntry item : xml) { String tag = item.getTag().trim(); String value = item.getValue(); if (tag.equals("package") || value.contains("com.app.base")) item.setValue(value.replace("com.app.base", pkg)); if (tag.equals("android:label") || tag.equals("label")) item.setValue(label); } replacement = new aXMLEncoder().encodeString(this, toXml(xml)); } } ZipEntry outEntry = new ZipEntry(entry); if (replacement != null) { outEntry.setMethod(ZipEntry.STORED); outEntry.setSize(replacement.length); CRC32 crc = new CRC32(); crc.update(replacement); outEntry.setCrc(crc.getValue()); outEntry.setCompressedSize(replacement.length); } out.putNextEntry(outEntry); if (replacement != null) out.write(replacement); else try (InputStream in = zip.getInputStream(entry)) { copy(in, out); } out.closeEntry(); } }
        File keyFile = selectedKeystore == null ? copyAssetKey() : copySelectedKeystore(); String pass = password(); KeyStore keyStore = loadKeyStore(keyFile, pass); String alias = firstPrivateKeyAlias(keyStore); Key key = keyStore.getKey(alias, pass.toCharArray()); if (!(key instanceof PrivateKey)) throw new GeneralSecurityException("No private key entry"); X509Certificate cert = (X509Certificate) keyStore.getCertificate(alias); ApkSigner.SignerConfig config = new ApkSigner.SignerConfig.Builder("KeepDroidMaker", (PrivateKey)key, Collections.singletonList(cert)).build(); new ApkSigner.Builder(Collections.singletonList(config)).setInputApk(unsigned).setOutputApk(signed).setV1SigningEnabled(true).setV2SigningEnabled(true).setV3SigningEnabled(false).setV4SigningEnabled(false).build().sign(); unsigned.delete(); return publishApk(signed, pkg);
    }
    private Uri publishApk(File source, String pkg) throws Exception { String name = pkg.replace('.', '_') + ".apk"; if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) { ContentValues values = new ContentValues(); values.put(MediaStore.MediaColumns.DISPLAY_NAME, name); values.put(MediaStore.MediaColumns.MIME_TYPE, "application/vnd.android.package-archive"); values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS + "/KeepDroid/apk"); values.put(MediaStore.MediaColumns.IS_PENDING, 1); Uri uri = getContentResolver().insert(MediaStore.Files.getContentUri("external"), values); if (uri == null) throw new IOException("Unable to create MediaStore entry"); try (InputStream in = new FileInputStream(source); OutputStream out = getContentResolver().openOutputStream(uri)) { copy(in, out); } values.clear(); values.put(MediaStore.MediaColumns.IS_PENDING, 0); getContentResolver().update(uri, values, null, null); return uri; } File folder = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "KeepDroid/apk"); if (!folder.exists() && !folder.mkdirs()) throw new IOException("Unable to create output folder"); File target = new File(folder, name); try (InputStream in = new FileInputStream(source); OutputStream out = new FileOutputStream(target)) { copy(in, out); } return FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", target); }
    private KeyStore loadKeyStore(File file, String pass) throws Exception { Exception last = null; for (String type : new String[]{"PKCS12", "JKS"}) try (InputStream in = new FileInputStream(file)) { KeyStore ks = KeyStore.getInstance(type); ks.load(in, pass.toCharArray()); return ks; } catch (Exception e) { last = e; } throw last; }
    private String firstPrivateKeyAlias(KeyStore ks) throws Exception { Enumeration<String> aliases = ks.aliases(); while (aliases.hasMoreElements()) { String alias = aliases.nextElement(); if (ks.isKeyEntry(alias)) return alias; } throw new GeneralSecurityException("No private key alias found"); }
    private String toXml(java.util.List<XMLEntry> entries) { StringBuilder b = new StringBuilder(); for (XMLEntry e : entries) b.append(e.getText()).append('\n'); return b.toString().trim(); }
    private void copy(InputStream in, OutputStream out) throws IOException { byte[] buf = new byte[8192]; int n; while ((n = in.read(buf)) != -1) out.write(buf, 0, n); }
    private void chooseApk() { startActivity(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("application/vnd.android.package-archive").addCategory(Intent.CATEGORY_OPENABLE)); }
    private void openInstaller(Uri uri) { startActivity(new Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)); }
}
