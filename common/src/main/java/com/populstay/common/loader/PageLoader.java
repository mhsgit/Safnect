package com.populstay.common.loader;

import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.StringRes;

import com.populstay.common.R;
import com.wang.avi.AVLoadingIndicatorView;


/**
 * 加载动画
 */

public class PageLoader {

	private static final int LOADER_SIZE_SCALE = 8;
	private static final int LOADER_OFFSET_SCALE = 10;
	private static final int LOADER_TEXT_NONE = -1;
	public static final String DEFAULT_LOADER = LoaderStyle.BallSpinFadeLoaderIndicator.name();
	AVLoadingIndicatorView avLoadingIndicatorView;

	public void showLoading(Enum<LoaderStyle> type, ViewGroup rootView) {
		showLoading(type.name(),rootView);
	}

	public void showLoading(String type, @StringRes int textStrRes, ViewGroup rootView) {
		avLoadingIndicatorView = LoaderCreator.create(type, rootView.getContext());
		avLoadingIndicatorView.setIndicatorColor(0xFF616366);

		FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(180,180);
		lp.gravity = Gravity.CENTER;
		lp.bottomMargin = 220;
		rootView.addView(avLoadingIndicatorView, 0, lp);
		if (LOADER_TEXT_NONE == textStrRes){
			TextView loadingTxt = rootView.findViewById(R.id.loading_txt);
			try{
				loadingTxt.setText(textStrRes);
			}catch (Exception e){
				e.printStackTrace();
				if (null != loadingTxt){
					loadingTxt.setVisibility(View.GONE);
				}
			}
		}
	}

	public void showLoading(String type, ViewGroup rootView) {
		showLoading(type, LOADER_TEXT_NONE,rootView);
	}

	public void showLoading(ViewGroup rootView) {
		showLoading(DEFAULT_LOADER,rootView);
	}

	public void stopLoading(ViewGroup rootView) {
		if (null != avLoadingIndicatorView){
			rootView.removeView(avLoadingIndicatorView);
			avLoadingIndicatorView = null;
		}
	}

}
