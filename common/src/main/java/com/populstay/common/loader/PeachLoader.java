package com.populstay.common.loader;

import android.app.Activity;
import android.content.Context;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatDialog;

import com.populstay.common.R;
import com.populstay.common.dimen.DimenUtil;
import com.wang.avi.AVLoadingIndicatorView;

import java.util.ArrayList;

/**
 * 加载动画
 */

public class PeachLoader {

	private static final int LOADER_SIZE_SCALE = 8;
	private static final int LOADER_OFFSET_SCALE = 10;
	private static final int LOADER_TEXT_NONE = -1;

	private static final ArrayList<AppCompatDialog> LOADERS = new ArrayList<>();

	public static final String DEFAULT_LOADER = LoaderStyle.BallSpinFadeLoaderIndicator.name();

	public static void showLoading(Context context, Enum<LoaderStyle> type) {
		showLoading(context, type.name());
	}

	public static void showLoading(Context context, String type, @StringRes int textStrRes) {
		if (!(context instanceof Activity)){
			return;
		}
		final AppCompatDialog dialog = new AppCompatDialog(context, R.style.dialog_theme);
		final AVLoadingIndicatorView avLoadingIndicatorView = LoaderCreator.create(type, context);

		if (LOADER_TEXT_NONE == textStrRes){
			dialog.setContentView(avLoadingIndicatorView);
			int deviceWidth = DimenUtil.getScreenWidth(context.getApplicationContext());
			int deviceHeight = DimenUtil.getScreenHeight(context.getApplicationContext());
			final Window dialogWindow = dialog.getWindow();
			if (dialogWindow != null) {
				final WindowManager.LayoutParams lp = dialogWindow.getAttributes();
				lp.width = deviceWidth / LOADER_SIZE_SCALE;
				lp.height = deviceHeight / LOADER_SIZE_SCALE;
				lp.height = lp.height + deviceHeight / LOADER_OFFSET_SCALE;
				lp.gravity = Gravity.CENTER;
			}
		}else {
			LinearLayout rootView = (LinearLayout) LayoutInflater.from(context).inflate(R.layout.loading_layout,null);
			LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(140,140);
			rootView.addView(avLoadingIndicatorView, 0, lp);
			dialog.setContentView(rootView);
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
		dialog.setCanceledOnTouchOutside(false);

		LOADERS.add(dialog);
		Activity activity = (Activity) context;
		if (!activity.isFinishing())
		{
			dialog.show();
		}
	}

	public static void showLoading(Context context, String type) {
		showLoading(context, type, LOADER_TEXT_NONE);
	}

	public static void updateLoading(String text) {
		for (AppCompatDialog dialog : LOADERS) {
			if (dialog != null) {
				if (dialog.isShowing()) {
					TextView loadingTxt = dialog.findViewById(R.id.loading_txt);
					loadingTxt.setText(text);
				}
			}
		}
	}


	public static void showLoading(Context context) {
		showLoading(context, DEFAULT_LOADER);
	}

	public static void stopLoading() {
		for (AppCompatDialog dialog : LOADERS) {
			if (dialog != null) {
				if (dialog.isShowing()) {
					dialog.cancel();
				}
			}
		}
	}

}
