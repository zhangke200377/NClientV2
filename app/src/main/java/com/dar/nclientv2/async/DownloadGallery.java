package com.dar.nclientv2.async;

import android.app.IntentService;
import android.app.PendingIntent;
import android.app.TaskStackBuilder;
import android.content.Intent;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.util.Log;

import com.dar.nclientv2.GalleryActivity;
import com.dar.nclientv2.R;
import com.dar.nclientv2.api.components.Gallery;
import com.dar.nclientv2.api.enums.TitleType;
import com.dar.nclientv2.settings.Global;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class DownloadGallery extends IntentService {
    private Gallery gallery;
    private NotificationCompat.Builder notification;
    private final int notId;
    private int page=0;
    private final Object lock;
    private final OkHttpClient client;
    private NotificationManagerCompat notificationManager;
    public DownloadGallery(){
        super("Download Gallery");
        notId=Global.getNotificationId();
        lock=new Object();
        client=new OkHttpClient();
    }
    private void downloadedPage(){
        notification.setProgress(gallery.getPageCount()-1,++page,false);
        notificationManager.notify(getString(R.string.channel_name),notId,notification.build());
        synchronized (lock){
            lock.notify();
        }
    }
    private void onPreExecute() {
        Global.addToDownloadList(gallery.getId());
        Intent resultIntent = new Intent(this, GalleryActivity.class);
        resultIntent.putExtra(getPackageName()+".GALLERY",gallery);
        resultIntent.putExtra(getPackageName()+".INSTANTDOWNLOAD",true);
        TaskStackBuilder stackBuilder = TaskStackBuilder.create(this);
        stackBuilder.addNextIntentWithParentStack(resultIntent);
        PendingIntent resultPendingIntent =stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);
        notificationManager = NotificationManagerCompat.from(getApplicationContext());
        notification=new NotificationCompat.Builder(getApplicationContext(), Global.CHANNEL_ID);
        //notification.addAction(R.drawable.ic_close,"Stop",new PendingIntent.)
        notification.setSmallIcon(R.drawable.ic_file)
                .setOnlyAlertOnce(true)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(gallery.getTitle(Global.getTitleType())))
                .setContentTitle(getString(R.string.notification_title))
                .setContentText(gallery.getTitle(TitleType.PRETTY))
                .setContentIntent(resultPendingIntent)
                .setProgress(gallery.getPageCount()-1,0,false)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setCategory(NotificationCompat.CATEGORY_STATUS);
        notificationManager.notify(getString(R.string.channel_name),notId,notification.build());
    }
    int a;
    @Override
    protected void onHandleIntent(@Nullable Intent intent) {
        gallery=intent.getParcelableExtra(getPackageName()+".GALLERY");
        if(gallery==null)return;
        if(Global.isDownloading(gallery.getId()))return;
        onPreExecute();
        Intent intent1=new Intent(this,DownloadGallery.class);
        intent1.setAction("stop");
        PendingIntent pStopSelf = PendingIntent.getService(this, 0, intent1, PendingIntent.FLAG_CANCEL_CURRENT);
        notification.addAction(R.drawable.ic_close,"Stop",pStopSelf);
        File folder=Global.findGalleryFolder(gallery.getId());
        if(folder==null){
            folder=new File(getString(R.string.download_path, Environment.getExternalStorageDirectory().getAbsolutePath(),
                    gallery.getTitle(Global.getTitleType()).length()<3?gallery.getTitle(TitleType.ENGLISH):gallery.getTitle(Global.getTitleType())));
            folder.mkdirs();
            File nomedia=new File(folder,".nomedia");
            try {
                nomedia.createNewFile();
                FileOutputStream ostream = new FileOutputStream(nomedia);
                ostream.write(Integer.toString(gallery.getId()).getBytes());
                ostream.flush();
                ostream.close();
            }catch (IOException e){
                Log.e("IOException", e.getLocalizedMessage()); }
        }
        for(a=0;a<gallery.getPageCount();a++){
            final File x=new File(folder,("000"+(a+1)+".jpg").substring(Integer.toString(a+1).length()));
            if(!x.exists()||Global.isCorrupted(x.getAbsolutePath())){
                client.newCall(new Request.Builder().url(gallery.getPage(a).getUrl()).build()).enqueue(new Callback() {
                    @Override
                    public void onFailure(@NonNull Call call,@NonNull IOException e) {
                        e.printStackTrace();
                        downloadedPage();
                    }
                    @Override
                    public void onResponse(@NonNull Call call,@NonNull Response response) {
                        new DownloadPage(response.body().byteStream(),x).start();
                        downloadedPage();
                    }
                });
                synchronized (lock){
                    try {
                        lock.wait();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
            else {downloadedPage();}
        }
        onPostExecute();
    }

    @Override
    public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
        if(intent!=null&&"stop".equals(intent.getAction()))a=999;
        Log.d(Global.LOGTAG,flags+","+startId);
        return super.onStartCommand(intent, flags, startId);
    }

    private void onPostExecute() {
        Global.removeFromDownloadList(gallery.getId());
        notification.setProgress(0,0,false);
        notification.mActions.clear();
        notification.setContentTitle(getString(a==999?R.string.downlaod_canceled:R.string.download_completed)).setOnlyAlertOnce(false);
        notificationManager.notify(getString(R.string.channel_name),notId,notification.build());
    }
}