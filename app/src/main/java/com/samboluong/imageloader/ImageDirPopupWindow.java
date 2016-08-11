package com.samboluong.imageloader;

import android.content.Context;
import android.graphics.drawable.BitmapDrawable;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.PopupWindow;

import com.samboluong.imageloader.adapter.ListDirAdapter;
import com.samboluong.imageloader.bean.FolderBean;

import java.util.List;

public class ImageDirPopupWindow extends PopupWindow {

    private int mWidth;
    private int mHeight;
    private View mConvertView;
    private ListView mListView;
    private List<FolderBean> mDataList;

    public interface OnDirSelectedListener {
        void onSelected(FolderBean folderBean);
    }

    public OnDirSelectedListener mListener;

    public void setOnDirSelectedListener(OnDirSelectedListener listener) {
        this.mListener = listener;
    }

    public ImageDirPopupWindow(Context context, List<FolderBean> mDataList) {
        calWidthAndHeight(context);

        mConvertView = LayoutInflater.from(context).inflate(R.layout.popup_main, null);
        this.mDataList = mDataList;

        setContentView(mConvertView);
        setWidth(mWidth);
        setHeight(mHeight);

        setFocusable(true);
        setTouchable(true);
        setOutsideTouchable(true);
        setBackgroundDrawable(new BitmapDrawable());

        setTouchInterceptor(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                // 点击外面消失
                if (event.getAction() == MotionEvent.ACTION_OUTSIDE) {
                    dismiss();
                    return true;
                }
                return false;
            }
        });

        initViews(context);
        initEvent();
    }

    private void initEvent() {
        mListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (mListener != null) {
                    mListener.onSelected(mDataList.get(position));
                }
            }
        });
    }

    private void initViews(Context context) {
        mListView = (ListView) mConvertView.findViewById(R.id.id_list_dir);
        mListView.setAdapter(new ListDirAdapter(context, mDataList));
    }

    /**
     * 计算popupWindow的宽高
     *
     * @param context
     */
    private void calWidthAndHeight(Context context) {
        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        DisplayMetrics metrics = new DisplayMetrics();
        wm.getDefaultDisplay().getMetrics(metrics);

        mWidth = metrics.widthPixels;
        mHeight = (int) (metrics.heightPixels * 0.7);
    }
}
