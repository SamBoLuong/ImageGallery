package com.samboluong.imageloader;

import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.WindowManager;
import android.widget.GridView;
import android.widget.PopupWindow;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.samboluong.imageloader.adapter.ImageAdapter;
import com.samboluong.imageloader.bean.FolderBean;

import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    private GridView mGridView;
    private List<String> mImgNames;
    private ImageAdapter mImgAdapter;

    private RelativeLayout mBottomLayout;
    private TextView mDirName;
    private TextView mDirCount;

    private File mCurrentDir;
    private int mMaxCount;

    private List<FolderBean> mFolderBeenList = new ArrayList<>();

    private ProgressDialog mProgressDialog;

    private static final int DATA_LOADED = 0x110;

    private ImageDirPopupWindow mDirPopupWindow;

    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            if (msg.what == DATA_LOADED) {
                mProgressDialog.dismiss();
                // 为GridView设置数据
                data2View();

                initDirPopupWindow();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initView();
        initData();
        initEvent();
    }

    private void initEvent() {
        mBottomLayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mDirPopupWindow.setAnimationStyle(R.style.dir_popupwindow_anim);
                mDirPopupWindow.showAsDropDown(mBottomLayout, 0, 0);
                lightOff();
            }
        });
    }

    private void initDirPopupWindow() {
        mDirPopupWindow = new ImageDirPopupWindow(this, mFolderBeenList);
        mDirPopupWindow.setOnDismissListener(new PopupWindow.OnDismissListener() {
            @Override
            public void onDismiss() {
                lightOn();
            }
        });

        mDirPopupWindow.setOnDirSelectedListener(new ImageDirPopupWindow.OnDirSelectedListener() {
            @Override
            public void onSelected(FolderBean folderBean) {
                mCurrentDir = new File(folderBean.getDir());

                mImgNames = Arrays.asList(mCurrentDir.list(new FilenameFilter() {
                    @Override
                    public boolean accept(File dir, String filename) {
                        return filename.endsWith(".jpg") || filename.endsWith(".jpeg") || filename.endsWith(".png");
                    }
                }));

                mImgAdapter = new ImageAdapter(MainActivity.this, mImgNames, mCurrentDir.getAbsolutePath());
                mGridView.setAdapter(mImgAdapter);

                mDirCount.setText(mImgNames.size() + "");
                mDirName.setText(folderBean.getName());

                mDirPopupWindow.dismiss();
            }
        });
    }

    /**
     * 内容区域变亮
     */
    private void lightOn() {
        WindowManager.LayoutParams params = getWindow().getAttributes();
        params.alpha = 1.0f;
        getWindow().setAttributes(params);
    }

    private void data2View() {
        if (mCurrentDir == null) {
            Toast.makeText(this, "未扫描到图片", Toast.LENGTH_SHORT).show();
            return;
        }

        // 因为mCurrentDir.list()返回的是一个数组
        mImgNames = Arrays.asList(mCurrentDir.list());
        mImgAdapter = new ImageAdapter(this, mImgNames, mCurrentDir.getAbsolutePath());
        mGridView.setAdapter(mImgAdapter);

        mDirCount.setText(mMaxCount + "");
        mDirName.setText(mCurrentDir.getName());
    }

    /**
     * 内容区域变暗
     */
    private void lightOff() {
        WindowManager.LayoutParams params = getWindow().getAttributes();
        params.alpha = 0.3f;
        getWindow().setAttributes(params);
    }

    /**
     * 利用ContentProvider扫描手机中的所有图片
     */
    private void initData() {
        if (!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            Toast.makeText(this, "当前存储卡不可用！", Toast.LENGTH_SHORT).show();
            return;
        }

        mProgressDialog = ProgressDialog.show(this, null, "正在加载...");

        new Thread() {
            @Override
            public void run() {
                Uri uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                ContentResolver cr = MainActivity.this.getContentResolver();

                Cursor cursor = cr.query(uri, null, MediaStore.Images.Media.MIME_TYPE + " = ? or "
                                + MediaStore.Images.Media.MIME_TYPE + " = ?",
                        new String[]{"image/jpeg", "image/png"},
                        MediaStore.Images.Media.DATE_MODIFIED);

                Set<String> mDirPathSet = new HashSet<>();

                FolderBean folderBean;
                while (cursor != null && cursor.moveToNext()) {
                    String path = cursor.getString(cursor.getColumnIndex(MediaStore.Images.Media.DATA)); // 完整路径
                    File parentFile = new File(path).getParentFile();

                    if (parentFile == null) continue;

                    String dirPath = parentFile.getAbsolutePath(); // 文件夹路径

                    // 防止重复扫描
                    if (mDirPathSet.contains(dirPath)) {
                        continue;
                    } else {
                        mDirPathSet.add(dirPath);
                        folderBean = new FolderBean();
                        folderBean.setDir(dirPath);
                        folderBean.setFirstImagePath(path); // 因为每个文件夹只会有第一张图片进来
                    }

                    if (parentFile.list() == null) continue;

                    int picSize = parentFile.list(new FilenameFilter() {
                        @Override
                        public boolean accept(File dir, String filename) {
                            if (filename.endsWith(".jpg") || filename.endsWith(".jpeg") || filename.endsWith(".png")) {
                                return true;
                            }
                            return false;
                        }
                    }).length;

                    folderBean.setCount(picSize);

                    mFolderBeenList.add(folderBean);

                    if (picSize > mMaxCount) {
                        mMaxCount = picSize;
                        mCurrentDir = parentFile;
                    }
                }

                if (cursor != null) cursor.close();

                // 通知Handler扫描图片完成
                mHandler.sendEmptyMessage(DATA_LOADED);
            }
        }.start();
    }

    private void initView() {
        mGridView = (GridView) findViewById(R.id.id_gridView);
        mBottomLayout = (RelativeLayout) findViewById(R.id.id_bottom_ly);
        mDirName = (TextView) findViewById(R.id.id_dir_name);
        mDirCount = (TextView) findViewById(R.id.id_dir_count);
    }
}
