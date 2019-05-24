package com.openchat.secureim.components.camera;

import android.content.Context;
import android.support.v4.view.animation.FastOutSlowInInterpolator;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.Animation.AnimationListener;
import android.view.animation.AnimationUtils;
import android.widget.ImageButton;

import com.openchat.secureim.R;

public class HidingImageButton extends ImageButton {
  public HidingImageButton(Context context) {
    super(context);
  }

  public HidingImageButton(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  public HidingImageButton(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
  }

  public void hide() {
    final Animation animation = AnimationUtils.loadAnimation(getContext(), R.anim.slide_to_right);
    animation.setAnimationListener(new AnimationListener() {
      @Override public void onAnimationStart(Animation animation) {}
      @Override public void onAnimationRepeat(Animation animation) {}
      @Override public void onAnimationEnd(Animation animation) {
        setVisibility(GONE);
      }
    });
    animateWith(animation);
  }

  public void show() {
    setVisibility(VISIBLE);
    animateWith(AnimationUtils.loadAnimation(getContext(), R.anim.slide_from_right));
  }

  private void animateWith(Animation animation) {
    animation.setDuration(150);
    animation.setInterpolator(new FastOutSlowInInterpolator());
    startAnimation(animation);
  }
}
