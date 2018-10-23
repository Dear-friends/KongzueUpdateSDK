package com.kongzue.kongzueupdatesdk;

import android.app.DownloadManager;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.support.v4.content.FileProvider;
import android.util.Log;
import android.widget.Toast;

import java.io.File;

import static android.content.Context.DOWNLOAD_SERVICE;

public class UpdateUtil {

    public static boolean isShowProgressDialog = true;          //是否显示更新进度对话框
    public static String hideProgressDialogButtonCaption = "后台下载";
    public static String cancelProgressDialogButtonCaption = "取消下载";
    public static String progressDialogTitle = "正在下载";

    private Context me;
    private String packageName;
    private OnDownloadListener onDownloadListener;

    private DownloadManager downloadManager;
    private long downloadId;
    private DownloadFinishReceiver mReceiver;
    private File file;

    private UpdateUtil() {
    }

    public UpdateUtil(Context me, String packageName) {
        this.me = me;
        this.packageName = packageName;
        downloadManager = (DownloadManager) me.getSystemService(DOWNLOAD_SERVICE);
    }

    public boolean doUpdate(UpdateInfo updateInfo) {
        if (updateInfo == null) {
            return false;
        }
        mReceiver = new DownloadFinishReceiver();
        me.registerReceiver(mReceiver, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(updateInfo.getDownloadUrl()));
        file = new File(me.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), updateInfo.getVer() + ".apk");
        if (file.exists()) file.delete();       //文件存在则删除
        Uri path = Uri.fromFile(file);
        log("path:" + path.toString());
        request.setDestinationUri(path);
        downloadId = downloadManager.enqueue(request);
        if (onDownloadListener != null) onDownloadListener.onStart(downloadId);
        doGetProgress();
        showProgressDialog();

        return true;
    }

    private void OpenWebBrowserAndOpenLink(String downloadUrl) {
        Intent intent = new Intent();
        intent.setAction("android.intent.action.VIEW");
        Uri content_url = Uri.parse(downloadUrl);
        intent.setData(content_url);
        me.startActivity(intent);
    }

    private void doGetProgress() {
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                int progress = getProgress(downloadId);
                log(progress);
                if (onDownloadListener != null)
                    onDownloadListener.onDownloading(downloadId, progress);
                if (progress != 100) {
                    doGetProgress();
                    if (progressDialog != null) progressDialog.setProgress(progress);
                } else {
                    if (progressDialog != null) progressDialog.dismiss();
                    installApk(me);
    
                    if (onDownloadListener != null) onDownloadListener.onSuccess(downloadId);
                    return;
                }
            }
        }, 10);
    }

    private void installApk(Context context) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Uri contentUri = FileProvider.getUriForFile(context, packageName + ".fileProvider", file);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.setDataAndType(contentUri, "application/vnd.android.package-archive");
        } else if (Build.VERSION.SDK_INT > Build.VERSION_CODES.M) {
            intent.setDataAndType(Uri.fromFile(getRealFileInAndroudM(context, downloadId)), "application/vnd.android.package-archive");
        } else {
            intent.setDataAndType(Uri.fromFile(file),
                    "application/vnd.android.package-archive");
        }
        context.startActivity(intent);
    }

    private File getRealFileInAndroudM(Context context, long downloadId) {
        File file = null;
        DownloadManager downloader = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        if (downloadId != -1) {
            DownloadManager.Query query = new DownloadManager.Query();
            query.setFilterById(downloadId);
            query.setFilterByStatus(DownloadManager.STATUS_SUCCESSFUL);
            Cursor cur = downloader.query(query);
            if (cur != null) {
                if (cur.moveToFirst()) {
                    String uriString = cur.getString(cur.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI));
                    if (!uriString.isEmpty()) {
                        file = new File(Uri.parse(uriString).getPath());
                    }
                }
                cur.close();
            }
        }
        return file;
    }

    private int getProgress(long downloadId) {
        DownloadManager.Query query = new DownloadManager.Query()
                .setFilterById(downloadId);
        Cursor cursor = null;
        int progress = 0;
        try {
            cursor = downloadManager.query(query);
            if (cursor != null && cursor.moveToFirst()) {
                int downloadSoFar = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));       //当前的下载量
                int totalBytes = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));          //文件总大小
                progress = (int) (downloadSoFar * 1.0f / totalBytes * 100);
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return progress;
    }

    public UpdateUtil showNormalUpdateDialog(final UpdateInfo updateInfo, int styleResId, String titleStr, String downloadByShopStr, String downloadNowStr, String cancelStr) {
        android.support.v7.app.AlertDialog.Builder builder;
        if (styleResId == -1) {
            builder = new android.support.v7.app.AlertDialog.Builder(me);
        } else {
            builder = new android.support.v7.app.AlertDialog.Builder(me, styleResId);
        }
        builder.setTitle(titleStr);
        builder.setCancelable(true);
        builder.setMessage(updateInfo.getInfo());
        builder.setPositiveButton(downloadNowStr, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                doUpdate(updateInfo);
            }
        });
        builder.setNeutralButton(downloadByShopStr, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                openMarket();
            }
        });
        builder.setNegativeButton(cancelStr, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {

            }
        });
        builder.show();
        return this;
    }

    public UpdateUtil showNormalUpdateDialog(final UpdateInfo updateInfo, String titleStr, String downloadByShopStr, String downloadNowStr, String cancelStr) {
        return showNormalUpdateDialog(updateInfo, -1, titleStr, downloadByShopStr, downloadNowStr, cancelStr);
    }

    private void openMarket() {
        try {
            String str = "market://details?id=" + packageName;
            Intent localIntent = new Intent(Intent.ACTION_VIEW);
            localIntent.setData(Uri.parse(str));
            me.startActivity(localIntent);
        } catch (Exception e) {
            // 打开应用商店失败 可能是没有手机没有安装应用市场
            e.printStackTrace();
            // 调用系统浏览器进入商城
            openLinkBySystem("https://www.coolapk.com/apk/" + packageName);
        }
    }

    private void openLinkBySystem(String url) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse(url));
        me.startActivity(intent);
    }

    private class DownloadFinishReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
//            long downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
//            if (onDownloadListener != null) onDownloadListener.onSuccess(downloadId);
        }
    }

    public OnDownloadListener getOnDownloadListener() {
        return onDownloadListener;
    }

    public UpdateUtil setOnDownloadListener(OnDownloadListener onDownloadListener) {
        this.onDownloadListener = onDownloadListener;
        return this;
    }

    public interface OnDownloadListener {

        void onStart(long downloadId);

        void onDownloading(long downloadId, int progress);

        void onSuccess(long downloadId);

    }

    public interface OnDownloadingListener {
    }

    private void log(Object o) {
        if (BuildConfig.DEBUG)
            Log.d(">>>", o.toString());
    }

    private boolean isWifi() {
        ConnectivityManager connectivityManager = (ConnectivityManager) me.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetInfo = connectivityManager.getActiveNetworkInfo();
        if (activeNetInfo != null
                && activeNetInfo.getType() == ConnectivityManager.TYPE_WIFI) {
            return true;
        }
        return false;
    }

    private ProgressDialog progressDialog;

    public void showProgressDialog() {
        if (!isShowProgressDialog) return;
        progressDialog = new ProgressDialog(me);
        progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);// 设置水平进度条
        progressDialog.setCancelable(true);// 设置是否可以通过点击Back键取消
        progressDialog.setCanceledOnTouchOutside(false);// 设置在点击Dialog外是否取消Dialog进度条
        progressDialog.setTitle(progressDialogTitle);
        progressDialog.setMax(100);
        progressDialog.setButton(DialogInterface.BUTTON_POSITIVE, hideProgressDialogButtonCaption,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        progressDialog.dismiss();
                    }
                });
        progressDialog.setButton(DialogInterface.BUTTON_NEUTRAL, cancelProgressDialogButtonCaption,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        progressDialog.dismiss();
                        downloadManager.remove(downloadId);
                    }
                });
        progressDialog.show();
    }
}
