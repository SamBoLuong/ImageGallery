package com.samboluong.imageloader.util;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.LruCache;
import android.view.ViewGroup;
import android.widget.ImageView;

import java.lang.reflect.Field;
import java.util.LinkedList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

/**
 * 图片加载类(单例)
 */
public class ImageLoader {

    private static ImageLoader mInstance;
    private static final int DEFAULT_THREAD_COUNT = 3;

    /**
     * 图片缓存核心对象
     */
    private LruCache<String, Bitmap> mLrUCache;
    /**
     * 线程池
     */
    private ExecutorService mThreadPool;
    /**
     * 队列调度方式(默认后进先出)
     */
    private static Type mType = Type.LIFO;
    /**
     * 任务队列
     */
    private LinkedList<Runnable> mTaskQueue;
    /**
     * 后台轮询线程
     */
    private Thread mPoolThread;
    private Handler mPoolThreadHandler;
    /**
     * UI线程中的Handler
     */
    private Handler mUIHandler;

    private Semaphore mPoolThreadHandlerSemaphore = new Semaphore(0);
    private Semaphore mThreadPoolSemaphore;

    public enum Type {
        FIFO, LIFO,
    }


    private ImageLoader(int threadCount, Type type) {
        init(threadCount, type);
    }

    public static ImageLoader getInstance() {
        if (mInstance == null) {
            synchronized (ImageLoader.class) {
                if (mInstance == null) {
                    mInstance = new ImageLoader(DEFAULT_THREAD_COUNT, mType);
                }
            }
        }
        return mInstance;
    }

    public static ImageLoader getInstance(int threadCount, Type type) {
        if (mInstance == null) {
            synchronized (ImageLoader.class) {
                if (mInstance == null) {
                    mInstance = new ImageLoader(threadCount, type);
                }
            }
        }
        return mInstance;
    }

    /**
     * 初始化操作
     *
     * @param threadCount
     * @param type
     */
    private void init(int threadCount, Type type) {
        // 开启后台轮询线程
        mPoolThread = new Thread() {
            @Override
            public void run() {
                Looper.prepare();

                mPoolThreadHandler = new Handler() {
                    @Override
                    public void handleMessage(Message msg) {
                        // 去线程池取出一个任务进行执行
                        // execute执行的是Runnable中的run方法
                        mThreadPool.execute(getTask());

                        try {
                            // 当超过信号量个数(后台线程支持并发数)的时候就阻塞
                            mThreadPoolSemaphore.acquire();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                };
                // mPoolThreadHandler初始化完成，释放一个信号量
                mPoolThreadHandlerSemaphore.release();

                Looper.loop();
            }
        };

        mPoolThread.start();

        // 获取我们应用的最大使用内存
        int maxMemory = (int) Runtime.getRuntime().maxMemory();
        int cacheMemory = maxMemory / 8;

        mLrUCache = new LruCache<String, Bitmap>(cacheMemory) {
            // 这么方法的目的：测量每个Bitmap的值？
            @Override
            protected int sizeOf(String key, Bitmap value) {
                return value.getRowBytes() * value.getHeight();
            }
        };

        mThreadPool = Executors.newFixedThreadPool(threadCount); // 创建线程池
        mTaskQueue = new LinkedList<Runnable>();
        mType = type;

        mThreadPoolSemaphore = new Semaphore(threadCount);
    }

    /**
     * 从任务队列取出一个方法
     *
     * @return
     */
    private synchronized Runnable getTask() {
        if (mType == Type.FIFO) {
            return mTaskQueue.removeFirst();
        } else if (mType == Type.LIFO) {
            return mTaskQueue.removeLast();
        }
        return null;
    }

    /**
     * 添加到任务队列
     * 使用synchronized同步，避免多个线程进来后都进行acquire()，造成死锁
     * 并且操作任务队列本应该进行同步
     *
     * @param runnable
     */
    private synchronized void addTask(Runnable runnable) {
        // 可能mPoolThreadHandler还没初始化完成,使用信号量(Semaphore),解决并发问题
        if (mPoolThreadHandler == null) {
            try {
                // 如果没有初始化完成，这里会阻塞
                mPoolThreadHandlerSemaphore.acquire();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        mTaskQueue.add(runnable);
        mPoolThreadHandler.sendEmptyMessage(0x110);
    }

    /**
     * 根据path为ImageView设置图片
     *
     * @param path
     * @param imageView
     */
    public void loadImage(final String path, final ImageView imageView) {
        // 防止调用多次，ImageView复用之后造成混乱
        imageView.setTag(path);

        if (mUIHandler == null) {
//            mUIHandler = new Handler(Looper.getMainLooper());
            mUIHandler = new Handler() {
                @Override
                public void handleMessage(Message msg) {
                    // 获取得到的图片(异步加载以后得到Bitmap)，为ImageView回调设置图片
                    ImageBeanHolder holder = (ImageBeanHolder) msg.obj;
                    Bitmap bm1 = holder.bitmap;
                    String path1 = holder.path;
                    ImageView imageView1 = holder.imageView;

                    // 将path与getTag路径进行比较
                    if (imageView1.getTag().toString().equals(path1)) {
                        imageView1.setImageBitmap(bm1); // ImageView是通过holder.imageView获得的
                    }
                }
            };
        }

        Bitmap bm = getBitmapFromLruCache(path);

        if (bm != null) {
            // 回调给mUIHandler处理
            refreshBitmap(imageView, path, bm);
        } else {
            addTask(new Runnable() {
                @Override
                public void run() {
                    // 加载图片
                    // 图片的压缩
                    // 1、获得图片需要显示的大小
                    ImageViewSize imageViewSize = getImageViewSize(imageView);
                    // 2、压缩图片
                    Bitmap bm = decodeSampleBitmap(path, imageViewSize.width, imageViewSize.height);
                    // 3、把图片加入到缓存
                    addBitmapToLruCache(path, bm);

                    // 回调给mUIHandler处理
                    refreshBitmap(imageView, path, bm);

                    // 释放信号量，线程池可以往下执行，获取下一个task
                    mThreadPoolSemaphore.release();
                }
            });
        }
    }

    /**
     * 将ImageView、path、bitmap 回调给mUIHandler处理
     *
     * @param imageView
     * @param path
     * @param bitmap
     */
    private void refreshBitmap(ImageView imageView, String path, Bitmap bitmap) {
        ImageBeanHolder holder = new ImageBeanHolder();
        holder.bitmap = bitmap;
        holder.path = path;
        holder.imageView = imageView;
        Message msg = Message.obtain();
        msg.obj = holder;
        mUIHandler.sendMessage(msg);
    }

    /**
     * 将图片加入缓存(LruCache)
     */
    private void addBitmapToLruCache(String path, Bitmap bitmap) {
        if (getBitmapFromLruCache(path) == null) {
            if (bitmap != null) {
                mLrUCache.put(path, bitmap);
            }
        }
    }

    /**
     * 根据图片需要显示的宽和高对图片进行压缩
     *
     * @param path
     * @param reqWidth
     * @param reqHeight
     * @return
     */
    private Bitmap decodeSampleBitmap(String path, int reqWidth, int reqHeight) {
        // 获得图片的宽和高，并不把图片加载到内存当中
        BitmapFactory.Options options = new BitmapFactory.Options();

        // 如果设为true，decode的Bitmap为null,只是把图片的宽高放在Options里
        // options.outWidth 和 options.outHeight就是我们想要的宽和高
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, options);

        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);

        // 使用获取到的inSampleSize再次解析图片
        options.inJustDecodeBounds = false;
        return BitmapFactory.decodeFile(path, options);
    }

    /**
     * 根据需求的宽和高计算图片实际的宽和高
     *
     * @param options
     * @param reqWidth
     * @param reqHeight
     * @return
     */
    private int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        // 源图片的宽高
        int width = options.outWidth;
        int height = options.outHeight;

        int inSampleSize = 1;

        if (width > reqWidth || height > reqHeight) {
            int widthRadio = Math.round(width * 1.0f / reqWidth);
            int heightRadio = Math.round(height * 1.0f / reqWidth);

            inSampleSize = Math.max(widthRadio, heightRadio); // inSampleSize越大，压得越小
        }

        return inSampleSize;
    }

    /**
     * 根据ImageView获取适当的压缩的宽高
     *
     * @param imageView
     * @return
     */
    private ImageViewSize getImageViewSize(ImageView imageView) {
        ImageViewSize imageSize = new ImageViewSize();
        DisplayMetrics metrics = imageView.getContext().getResources().getDisplayMetrics();
        ViewGroup.LayoutParams params = imageView.getLayoutParams();

        int width = imageView.getWidth(); // ImageView的实际宽度
        if (width <= 0) width = params.width; // ImageView在Layout中声明的宽度
        if (width <= 0) width = imageView.getMaxWidth(); // 检查最大值
        if (width <= 0) width = metrics.widthPixels; // 屏幕宽度

        int height = imageView.getHeight(); // ImageView的实际高度
        if (height <= 0) height = params.height; // ImageView在Layout中声明的高度
        if (height <= 0) height = imageView.getMaxHeight(); // 检查最大值
        if (height <= 0) height = metrics.heightPixels; // 屏幕高度

        imageSize.width = width;
        imageSize.height = height;

        return imageSize;
    }

    /**
     * 反射
     */
    private static int getImageViewFieldValue(Object object, String fieldName) {
        int value = 0;
        try {
            Field field = ImageView.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            int fieldValue = (Integer) field.get(object);
            if (fieldValue > 0 && fieldValue < Integer.MAX_VALUE) {
                value = fieldValue;

                Log.e("TAG", value + "");
            }
        } catch (Exception ignored) {
        }
        return value;
    }

    /**
     * 根据path在缓存中获取Bitmap
     *
     * @param key
     * @return
     */
    private Bitmap getBitmapFromLruCache(String key) {
        return mLrUCache.get(key);
    }

    private class ImageViewSize {
        int width;
        int height;
    }

    private class ImageBeanHolder {
        Bitmap bitmap;
        ImageView imageView;
        String path;
    }
}
