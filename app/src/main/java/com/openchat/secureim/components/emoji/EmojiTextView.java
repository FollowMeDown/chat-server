package com.openchat.secureim.components.emoji;

import android.content.Context;
import android.graphics.Paint.FontMetricsInt;
import android.graphics.drawable.Drawable;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.widget.AppCompatTextView;
import android.text.TextUtils;
import android.text.TextUtils.TruncateAt;
import android.util.AttributeSet;

import com.openchat.secureim.components.emoji.EmojiProvider.EmojiDrawable;
import com.openchat.secureim.util.OpenchatServicePreferences;
import com.openchat.secureim.util.ViewUtil;

public class EmojiTextView extends AppCompatTextView {
  private CharSequence source;
  private boolean      needsEllipsizing;
  private boolean      useSystemEmoji;

  public EmojiTextView(Context context) {
    this(context, null);
  }

  public EmojiTextView(Context context, AttributeSet attrs) {
    this(context, attrs, 0);
  }

  public EmojiTextView(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    this.useSystemEmoji = OpenchatServicePreferences.isSystemEmojiPreferred(getContext());
  }

  @Override public void setText(@Nullable CharSequence text, BufferType type) {
    if (useSystemEmoji) {
      super.setText(text, type);
      return;
    }

    source = EmojiProvider.getInstance(getContext()).emojify(text, this);
    setTextEllipsized(source);
  }

  private void setTextEllipsized(final @Nullable CharSequence source) {
    super.setText(needsEllipsizing ? ViewUtil.ellipsize(source, this) : source, BufferType.SPANNABLE);
  }

  @Override public void invalidateDrawable(@NonNull Drawable drawable) {
    if (drawable instanceof EmojiDrawable) invalidate();
    else                                   super.invalidateDrawable(drawable);
  }

  @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    final int size = MeasureSpec.getSize(widthMeasureSpec);
    final int mode = MeasureSpec.getMode(widthMeasureSpec);
    if (!useSystemEmoji                                              &&
        getEllipsize() == TruncateAt.END                             &&
        !TextUtils.isEmpty(source)                                   &&
        (mode == MeasureSpec.AT_MOST || mode == MeasureSpec.EXACTLY) &&
        getPaint().breakText(source, 0, source.length()-1, true, size, null) != source.length())
    {
      needsEllipsizing = true;
      FontMetricsInt font = getPaint().getFontMetricsInt();
      super.onMeasure(MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY),
                      MeasureSpec.makeMeasureSpec(Math.abs(font.top - font.bottom), MeasureSpec.EXACTLY));
    } else {
      needsEllipsizing = false;
      super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }
  }

  @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
    if (changed && !useSystemEmoji) setTextEllipsized(source);
    super.onLayout(changed, left, top, right, bottom);
  }
}
