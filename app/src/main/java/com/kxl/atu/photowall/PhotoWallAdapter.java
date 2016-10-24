package com.kxl.atu.photowall;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.util.LruCache;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.ArrayAdapter;
import android.widget.GridView;
import android.widget.ImageView;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;

/**
 * Created by Administrator on 2016/10/24.
 */
public class PhotoWallAdapter extends ArrayAdapter<String> implements AbsListView.OnScrollListener {
    /**
     * 记录所有正在下载或等待下载的任务。
     */
    private Set<BitmapWorkerTask> taskCollection;

    /**
     * 图片缓存技术的核心类，用于缓存所有下载好的图片，在程序内存达到设定值时会将最少最近使用的图片移除掉。
     */
    private LruCache<String, Bitmap> mMemoryCache;

    /**
     * GridView的实例
     */
    private GridView mPhotoWall;

    /**
     * 第一张可见图片的下标
     */
    private int mFirstVisibleItem;

    /**
     * 一屏有多少张图片可见
     */
    private int mVisibleItemCount;

    /**
     * 记录是否刚打开程序，用于解决进入程序不滚动屏幕，不会下载图片的问题。
     */
    private boolean isFirstEnter = true;

    public PhotoWallAdapter(Context context, int textViewResourceId, String[] objects, GridView photoWall) {
        super(context, textViewResourceId, objects);
        mPhotoWall = photoWall;
        taskCollection = new HashSet<BitmapWorkerTask>();
        //获取应用程序可用的最大可用内存
        int maxMemary = (int) Runtime.getRuntime().maxMemory();
        int cacheSize = maxMemary / 8;
        // 设置图片缓存大小为程序最大可用内存的1/8
        mMemoryCache = new LruCache<String, Bitmap>(cacheSize) {
            @Override
            protected int sizeOf(String key, Bitmap value) {
                return value.getByteCount();
            }
        };
        mPhotoWall.setOnScrollListener(this);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        final String url = getItem(position);
        View view;
        if (convertView == null) {
            view = LayoutInflater.from(getContext()).inflate(R.layout.photo_layout, null);
        } else {
            view = convertView;
        }
        final ImageView imageView = (ImageView) view.findViewById(R.id.photo);
        // 给ImageView设置一个Tag，保证异步加载图片时不会乱序
        imageView.setTag(url);
        setImageView(url, imageView);
        return view;
    }

    /**
     * 给ImageView设置图片，首先从LruCache中获取缓存图片，设置到imageview上，
     * 如果没有缓存的图片，就给他设置一张默认的
     *
     * @param url       图片的url地址，lrucache中的键
     * @param imageView 用于显示图片的控件
     */
    private void setImageView(String url, ImageView imageView) {
        Bitmap bitmap = getBitmapFromMemoryCache(url);
        if (bitmap != null) {
            imageView.setImageBitmap(bitmap);
        } else {
            imageView.setImageResource(R.drawable.a);
        }
    }

    class BitmapWorkerTask extends AsyncTask<String, Void, Bitmap> {

        /**
         * 图片的url
         */
        private String imageUrl;

        @Override
        protected Bitmap doInBackground(String... params) {
            imageUrl = params[0];
            Bitmap bitmap = downloadBitmap(params[0]);
            if (bitmap != null) {
                //图片下载完成后缓存到Lrucache中
                addBitmap2Memary(params[0], bitmap);
            }
            return bitmap;
        }

        @Override
        protected void onPostExecute(Bitmap bitmap) {
            super.onPostExecute(bitmap);
            // 根据Tag找到相应的ImageView控件，将下载好的图片显示出来。
            ImageView image= (ImageView) mPhotoWall.findViewWithTag(imageUrl);
            if (bitmap!=null&&image!=null){
                image.setImageBitmap(bitmap);
            }
            taskCollection.remove(this);
        }
    }

    /**
     * 将一张图缓存到Lrucache中
     *
     * @param param  LruCache中的键，这里传入的是图片的url地址
     * @param bitmap LruCache中的键传入的是从网络中下载的比bitmap对象
     */
    private void addBitmap2Memary(String param, Bitmap bitmap) {
        if (getBitmapFromMemoryCache(param) == null) {
            mMemoryCache.put(param, bitmap);
        }
    }

    /**
     * 从LruCache中获取一图片，没有就返回null
     *
     * @param param 传入的图片的url地址
     * @return 对应键的bitmap对象  或者是null
     */
    private Bitmap getBitmapFromMemoryCache(String param) {
        return mMemoryCache.get(param);
    }

    /**
     * 建立HTTP请求，并获取bitmap对象
     *
     * @param param 图片的url地址
     * @return 返回的bitmap对象
     */
    private Bitmap downloadBitmap(String param) {
        Bitmap bitmap = null;
        HttpURLConnection con = null;
        try {
            URL url = new URL(param);
            con = (HttpURLConnection) url.openConnection();
            con.setConnectTimeout(5 * 1000);
            con.setReadTimeout(10 * 1000);
            con.setDoInput(true);
            con.setDoOutput(true);
            bitmap = BitmapFactory.decodeStream(con.getInputStream());

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (con != null)
                con.disconnect();
        }

        return bitmap;
    }

    @Override
    public void onScrollStateChanged(AbsListView view, int scrollState) {
        //仅当gridview静止时才去下载图片，滑动时取消下载
        if (scrollState == SCROLL_STATE_IDLE) {
            loadBitmaps(mFirstVisibleItem, mVisibleItemCount);
        } else {
            cancleAllTasks();
        }
    }

    /**
     * 取消所有正在下载或者等待下载的任务
     */
    public void cancleAllTasks() {
        if (taskCollection != null) {
            for (BitmapWorkerTask task : taskCollection
                    ) {
                task.cancel(false);
            }
        }
    }

    private void loadBitmaps(int mFirstVisibleItem, int mVisibleItemCount) {
        try {
            for (int i = mFirstVisibleItem; i < mFirstVisibleItem + mVisibleItemCount; i++) {
                String imageUrl = Images.imageThumbUrls[i];
                Bitmap bitmap = getBitmapFromMemoryCache(imageUrl);
                if (bitmap == null) {
                    BitmapWorkerTask bitmapWorkerTask = new BitmapWorkerTask();
                    taskCollection.add(bitmapWorkerTask);
                    bitmapWorkerTask.execute(imageUrl);
                } else {
                    ImageView imageView = (ImageView) mPhotoWall.findViewWithTag(imageUrl);
                    if (bitmap != null && imageView != null) {
                        imageView.setImageBitmap(bitmap);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
        mFirstVisibleItem = firstVisibleItem;
        mVisibleItemCount = visibleItemCount;
        // 下载的任务应该由onScrollStateChanged里调用，但首次进入程序时onScrollStateChanged并不会调用，
        // 因此在这里为首次进入程序开启下载任务。
        if (isFirstEnter && visibleItemCount > 0) {
            loadBitmaps(firstVisibleItem, visibleItemCount);
            isFirstEnter = false;
        }
    }
}
