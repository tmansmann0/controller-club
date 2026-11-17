package club.thor.pads;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.Locale;

public class ClubUi {
  public interface Actions { void choose(int slot); void toggle(); void pair(); void split(); void emulators(); void bluetooth(); void move(); }
  public static class Card {
    public final String title, detail, hardware;
    final int slotColor, controllerColor, rightColor;
    Card(String title,String detail,String hardware,int slotColor,int controllerColor,int rightColor) {
      this.title=title;this.detail=detail;this.hardware=hardware;
      this.slotColor=slotColor;this.controllerColor=controllerColor;this.rightColor=rightColor;
    }
  }

  static final int BG = 0xFF0E0F12, SURF = 0xFF15171A, PRESS = 0xFF1E2126, LINE = 0xFF2E3138, DIM = 0xFF3C4048,
      TEXT = 0xFFF1F2F4, SOFT = 0xFFC3C7CF, MUTED = 0xFF868B95, FOCUS = 0xFF00C8F0, BLUE = 0xFF00B4E6, RED = 0xFFFF4757, K = 0xFF141518;
  static final int[] SEAT = {RED, BLUE, 0xFFFFC53D, 0xFF3DDC84};
  static final String KNOWN = " thor joy_left joy_right joy_pair switch_pro wii_remote xbox xbox_nano playstation steam generic empty ";

  public static View build(Context ctx, Card[] cards, String status, boolean active, boolean paired, boolean showJoyCons, boolean lower, Actions actions) {
    final Actions a = actions;
    final boolean pr = paired;
    ScrollView sv = new ScrollView(ctx);
    sv.setFillViewport(true);
    sv.setFitsSystemWindows(true);
    sv.setBackgroundColor(BG);
    sv.setVerticalScrollBarEnabled(false);
    sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
    LinearLayout root = lin(ctx, LinearLayout.VERTICAL);
    root.setPadding(dp(ctx, 12), 0, dp(ctx, 12), dp(ctx, 10));
    root.setClipChildren(false);
    sv.addView(root, new ScrollView.LayoutParams(-1, -1));

    LinearLayout top = lin(ctx, LinearLayout.HORIZONTAL);
    top.setGravity(Gravity.CENTER_VERTICAL);
    top.setMinimumHeight(dp(ctx, 54));
    LinearLayout names = lin(ctx, LinearLayout.VERTICAL);
    names.addView(text(ctx, "Controller Club", 17, TEXT, true));
    int filled = 0;
    for (int i = 0; i < 4; i++) if (!isEmpty(at(cards, i))) filled++;
    TextView sub = text(ctx, filled + " of 4 seats filled", 12, MUTED, false);
    sub.setPadding(0, dp(ctx, 3), 0, 0);
    names.addView(sub);
    top.addView(names, new LinearLayout.LayoutParams(0, -2, 1));
    View mv = button(ctx, lower ? "Move to top" : "Move to bottom", new Glyph(6, TEXT, lower), 0, TEXT, a::move);
    mv.setContentDescription(lower ? "Move Controller Club to the top screen" : "Move Controller Club to the bottom screen");
    top.addView(mv, new LinearLayout.LayoutParams(-2, dp(ctx, 40)));
    root.addView(top, new LinearLayout.LayoutParams(-1, -2));
    root.addView(rule(ctx), new LinearLayout.LayoutParams(-1, hair(ctx)));

    LinearLayout seats = lin(ctx, LinearLayout.HORIZONTAL);
    seats.setClipChildren(false);
    seats.setMinimumHeight(dp(ctx, 212));
    for (int i = 0; i < 4; i++) {
      LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, 1);
      lp.setMargins(i == 0 ? 0 : dp(ctx, 4), 0, i == 3 ? 0 : dp(ctx, 4), 0);
      seats.addView(seat(ctx, i, at(cards, i), a), lp);
    }
    LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, 0, 1);
    sp.topMargin = dp(ctx, 12);
    root.addView(seats, sp);

    TextView st = text(ctx, "", 13, SOFT, false);
    SpannableStringBuilder sb = new SpannableStringBuilder(active ? "\u25CF  ROUTING ON" : "\u25CB  ROUTING OFF");
    sb.setSpan(new ForegroundColorSpan(active ? FOCUS : MUTED), 0, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    String s = status == null ? "" : status.trim();
    if (!s.isEmpty()) sb.append("   ").append(s);
    st.setText(sb);
    st.setGravity(Gravity.CENTER);
    st.setMaxLines(2);
    st.setEllipsize(TextUtils.TruncateAt.END);
    st.setLineSpacing(dp(ctx, 2), 1f);
    st.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
    LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(-1, -2);
    sl.setMargins(0, dp(ctx, 10), 0, dp(ctx, 10));
    root.addView(st, sl);
    root.addView(rule(ctx), new LinearLayout.LayoutParams(-1, hair(ctx)));

    LinearLayout bar = lin(ctx, LinearLayout.HORIZONTAL);
    View[] bs = {
        button(ctx, active ? "Stop routing" : "Start routing", new Glyph(active ? 1 : 0, active ? TEXT : BG, false), active ? RED : TEXT, active ? TEXT : BG, a::toggle),
        button(ctx, pr ? "Split Joy-Cons" : "Combine Joy-Cons", new Glyph(pr ? 3 : 2, TEXT, false), 0, TEXT, () -> { if (pr) a.split(); else a.pair(); }),
        button(ctx, "Nearby", new Glyph(4, TEXT, false), 0, TEXT, a::bluetooth),
        button(ctx, "Emulators", new Glyph(5, TEXT, false), 0, TEXT, a::emulators)};
    float[] w = {1.3f, 1.5f, 1f, 1f};
    for (int i = 0; i < 4; i++) {
      if(i==1&&!showJoyCons)continue;
      LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(ctx, 46), w[i]);
      lp.setMargins(i == 0 ? 0 : dp(ctx, 4), 0, i == 3 ? 0 : dp(ctx, 4), 0);
      bar.addView(bs[i], lp);
    }
    LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(-1, -2);
    bl.topMargin = dp(ctx, 10);
    root.addView(bar, bl);
    return sv;
  }

  static View seat(Context c, final int i, Card k, final Actions a) {
    boolean empty = isEmpty(k);
    String t = empty ? "Open slot" : k.title.trim();
    String det = k == null || k.detail == null ? "" : k.detail.trim();
    int col = k.slotColor;
    int marker = empty ? (col == SEAT[i] ? MUTED : col) : col;
    LinearLayout v = lin(c, LinearLayout.VERTICAL);
    v.setGravity(Gravity.CENTER_HORIZONTAL);
    v.setPadding(dp(c, 6), dp(c, 9), dp(c, 6), dp(c, 9));
    v.setBackground(states(c, empty ? 0 : SURF, empty ? DIM : LINE, 14, empty));
    LinearLayout head = lin(c, LinearLayout.HORIZONTAL);
    head.setGravity(Gravity.CENTER_VERTICAL);
    head.addView(text(c, String.valueOf(i + 1), 15, marker, true));
    for (int j = 0; j < 4; j++) {
      View led = new View(c);
      led.setBackground(shape(c, j <= i ? marker : 0xFF2A2D33, 0, 0, 1, false));
      LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(dp(c, 7), dp(c, 3));
      ll.leftMargin = dp(c, j == 0 ? 8 : 3);
      head.addView(led, ll);
    }
    v.addView(head, new LinearLayout.LayoutParams(-2, -2));
    LinearLayout.LayoutParams al = new LinearLayout.LayoutParams(-1, 0, 1);
    al.topMargin = dp(c, 4);
    v.addView(new Art(c, hw(k), empty ? DIM : col, k.controllerColor, k.rightColor), al);
    TextView tt = text(c, t, 13.5f, empty ? MUTED : TEXT, true);
    tt.setMinLines(2);
    tt.setMaxLines(2);
    tt.setEllipsize(TextUtils.TruncateAt.END);
    tt.setGravity(Gravity.CENTER);
    LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(-1, -2);
    tl.topMargin = dp(c, 6);
    v.addView(tt, tl);
    TextView dt = text(c, det, 11.5f, MUTED, false);
    dt.setMinLines(2);
    dt.setMaxLines(2);
    dt.setEllipsize(TextUtils.TruncateAt.END);
    dt.setGravity(Gravity.CENTER_HORIZONTAL);
    dt.setLineSpacing(dp(c, 1), 1f);
    LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(-1, -2);
    dl.topMargin = dp(c, 3);
    v.addView(dt, dl);
    tap(v, "Player " + (i + 1) + ", " + t + (det.isEmpty() ? "" : ", " + det), () -> a.choose(i));
    return v;
  }

  static View button(Context c, String label, Glyph g, int fill, int fg, Runnable r) {
    LinearLayout b = lin(c, LinearLayout.HORIZONTAL);
    b.setGravity(Gravity.CENTER);
    b.setPadding(dp(c, 10), 0, dp(c, 10), 0);
    b.setMinimumHeight(dp(c, 40));
    if (g != null) {
      ImageView iv = new ImageView(c);
      iv.setImageDrawable(g);
      iv.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
      b.addView(iv, new LinearLayout.LayoutParams(dp(c, 15), dp(c, 15)));
    }
    TextView t = text(c, label, 13, fg, true);
    t.setSingleLine(true);
    t.setEllipsize(TextUtils.TruncateAt.END);
    LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(-2, -2);
    tl.leftMargin = g == null ? 0 : dp(c, 6);
    b.addView(t, tl);
    b.setBackground(states(c, fill, fill == 0 ? LINE : fill, 10, false));
    tap(b, label, r);
    return b;
  }

  static void tap(View v, String desc, final Runnable r) {
    v.setClickable(true);
    v.setFocusable(true);
    v.setContentDescription(desc);
    if (Build.VERSION.SDK_INT >= 26) v.setDefaultFocusHighlightEnabled(false);
    v.setOnClickListener(x -> r.run());
    v.setOnFocusChangeListener((x, f) -> x.animate().scaleX(f ? 1.03f : 1f).scaleY(f ? 1.03f : 1f).setDuration(120).start());
    v.setAccessibilityDelegate(new View.AccessibilityDelegate() {
      @Override public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(host, info);
        info.setClassName(Button.class.getName());
      }
    });
  }

  static Drawable states(Context c, int fill, int line, float r, boolean dashed) {
    StateListDrawable s = new StateListDrawable();
    s.addState(new int[]{android.R.attr.state_focused}, shape(c, fill == 0 ? SURF : fill, FOCUS, 2.5f, r, false));
    s.addState(new int[]{android.R.attr.state_pressed}, shape(c, fill == 0 ? PRESS : mix(fill, 0xFF000000, .14f), line, 0.6f, r, dashed));
    s.addState(new int[0], shape(c, fill, line, 0.6f, r, dashed));
    return new RippleDrawable(ColorStateList.valueOf(0x1FFFFFFF), s, shape(c, 0xFFFFFFFF, 0, 0, r, false));
  }

  static GradientDrawable shape(Context c, int fill, int stroke, float sw, float r, boolean dashed) {
    GradientDrawable g = new GradientDrawable();
    g.setColor(fill);
    g.setCornerRadius(dp(c, r));
    if (sw > 0) {
      int px = Math.max(1, dp(c, sw));
      if (dashed) g.setStroke(px, stroke, dp(c, 5), dp(c, 4)); else g.setStroke(px, stroke);
    }
    return g;
  }

  static class Glyph extends Drawable {
    final int type, color; final boolean flag;
    final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    final RectF r = new RectF();
    Glyph(int type, int color, boolean flag) {
      this.type = type; this.color = color; this.flag = flag;
      p.setStrokeCap(Paint.Cap.ROUND); p.setStrokeJoin(Paint.Join.ROUND);
    }
    @Override public void draw(Canvas c) {
      Rect b = getBounds();
      float s = Math.min(b.width(), b.height()) / 24f;
      c.save(); c.translate(b.left, b.top); c.scale(s, s);
      p.setColor(color); p.setStrokeWidth(2f); p.setStyle(Paint.Style.FILL);
      Path path = new Path();
      switch (type) {
        case 0: path.moveTo(7, 4.5f); path.lineTo(19.5f, 12); path.lineTo(7, 19.5f); path.close(); c.drawPath(path, p); break;
        case 1: r.set(6, 6, 18, 18); c.drawRoundRect(r, 2.5f, 2.5f, p); break;
        case 2: case 3: {
          float g = type == 2 ? 0.8f : 3.5f;
          path.addRoundRect(new RectF(12 - g - 8, 3, 12 - g, 21), new float[]{8, 8, 1.5f, 1.5f, 1.5f, 1.5f, 8, 8}, Path.Direction.CW);
          p.setColor(BLUE); c.drawPath(path, p); path.reset();
          path.addRoundRect(new RectF(12 + g, 3, 12 + g + 8, 21), new float[]{1.5f, 1.5f, 8, 8, 8, 8, 1.5f, 1.5f}, Path.Direction.CW);
          p.setColor(RED); c.drawPath(path, p); break;
        }
        case 4:
          p.setStyle(Paint.Style.STROKE);
          path.moveTo(6.5f, 8); path.lineTo(17, 16.5f); path.lineTo(12, 20.5f); path.lineTo(12, 3.5f); path.lineTo(17, 7.5f); path.lineTo(6.5f, 16);
          c.drawPath(path, p); break;
        case 5:
          p.setStyle(Paint.Style.STROKE); r.set(2.5f, 7, 21.5f, 18); c.drawRoundRect(r, 5, 5, p);
          c.drawLine(6, 12.5f, 10, 12.5f, p); c.drawLine(8, 10.5f, 8, 14.5f, p);
          p.setStyle(Paint.Style.FILL); c.drawCircle(15.5f, 11, 1.3f, p); c.drawCircle(18, 13.8f, 1.3f, p); break;
        default: {
          p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(1.8f);
          RectF a1 = new RectF(4, 2.5f, 20, 11), a2 = new RectF(6, 13, 18, 21.5f);
          c.drawRoundRect(a1, 2, 2, p); c.drawRoundRect(a2, 2, 2, p);
          p.setStyle(Paint.Style.FILL); c.drawRoundRect(flag ? a1 : a2, 2, 2, p);
        }
      }
      c.restore();
    }
    @Override public void setAlpha(int alpha) { }
    @Override public void setColorFilter(ColorFilter cf) { p.setColorFilter(cf); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
  }

  static class Art extends View {
    final String hw; final int accent, shellColor, rightColor;
    final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG), s = new Paint(Paint.ANTI_ALIAS_FLAG);
    final RectF r = new RectF();
    float hair;

    Art(Context c, String hw, int accent) {
      this(c,hw,accent,0,0);
    }
    Art(Context c, String hw, int accent, int shellColor, int rightColor) {
      super(c); this.hw = hw; this.accent = accent; this.shellColor=shellColor; this.rightColor=rightColor;
      setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
      p.setStyle(Paint.Style.FILL);
      s.setStyle(Paint.Style.STROKE); s.setStrokeCap(Paint.Cap.ROUND); s.setStrokeJoin(Paint.Join.ROUND);
    }

    static float[] box(String h) {
      switch (h) {
        case "thor": return new float[]{150, 178};
        case "joy_left": case "joy_right": return new float[]{60, 148};
        case "joy_pair": return new float[]{150, 150};
        case "wii_remote": return new float[]{56, 160};
        case "xbox_nano": return new float[]{140, 98};
        case "switch_pro": return new float[]{160, 112};
        case "xbox": case "playstation": case "steam": return new float[]{160, 116};
        default: return new float[]{150, 100};
      }
    }

    @Override protected void onDraw(Canvas c) {
      float d = getResources().getDisplayMetrics().density, base = 10 * d;
      float[] b = box(hw);
      float aw = getWidth() - 6 * d, ah = getHeight() - base - 4 * d;
      if (aw <= 0 || ah <= 0) return;
      float k = Math.min(Math.min(aw / b[0], ah / b[1]), 1.5f * d);
      hair = 1.1f * d / k;
      c.save();
      c.translate((getWidth() - b[0] * k) / 2f, 2 * d + (ah - b[1] * k) / 2f);
      c.scale(k, k);
      switch (hw) {
        case "thor": thor(c); break;
        case "joy_left": c.translate(4, 4); joy(c, true, shellColor==0?BLUE:shellColor); break;
        case "joy_right": c.translate(4, 4); joy(c, false, shellColor==0?RED:shellColor); break;
        case "joy_pair": pair(c); break;
        case "switch_pro": pro(c); break;
        case "wii_remote": wii(c); break;
        case "xbox": xbox(c); break;
        case "xbox_nano": nano(c); break;
        case "playstation": ps(c); break;
        case "steam": steam(c); break;
        case "empty": empty(c); break;
        default: generic(c);
      }
      c.restore();
      if (!"empty".equals(hw)) {
        p.setShader(null); p.setColor(shellColor==0?accent:shellColor);
        float cx = getWidth() / 2f;
        r.set(cx - 16 * d, getHeight() - 3.5f * d, cx + 16 * d, getHeight() - d);
        c.drawRoundRect(r, d, d, p);
      }
    }

    Path rrp(float l, float t, float rt, float bt, float rad) { Path x = new Path(); x.addRoundRect(new RectF(l, t, rt, bt), rad, rad, Path.Direction.CW); return x; }
    Path ov(float l, float t, float rt, float bt) { Path x = new Path(); x.addOval(new RectF(l, t, rt, bt), Path.Direction.CW); return x; }
    Path union(Path... ps) { Path o = new Path(ps[0]); for (int i = 1; i < ps.length; i++) o.op(ps[i], Path.Op.UNION); return o; }

    void rr(Canvas c, float l, float t, float rt, float bt, float rad, int col) { p.setShader(null); p.setColor(col); r.set(l, t, rt, bt); c.drawRoundRect(r, rad, rad, p); }
    void dot(Canvas c, float x, float y, float rad, int col, int edge) {
      p.setShader(null); p.setColor(col); c.drawCircle(x, y, rad, p);
      if (edge != 0) { s.setColor(edge); s.setStrokeWidth(hair); c.drawCircle(x, y, rad, s); }
    }
    void diamond(Canvas c, float x, float y, float sp, float rad, int top, int right, int bottom, int left, int edge) {
      dot(c, x, y - sp, rad, top, edge); dot(c, x + sp, y, rad, right, edge); dot(c, x, y + sp, rad, bottom, edge); dot(c, x - sp, y, rad, left, edge);
    }
    void dpad(Canvas c, float x, float y, float size, int col) {
      float a = size / 2, w = size * 0.18f;
      rr(c, x - a, y - w, x + a, y + w, w * .6f, col); rr(c, x - w, y - a, x + w, y + a, w * .6f, col);
      dot(c, x, y, w * .45f, mix(col, 0xFF000000, .3f), 0);
    }
    void stick(Canvas c, float x, float y, float rad) {
      dot(c, x, y, rad, 0xFF0D0E10, 0); dot(c, x, y, rad * .74f, 0xFF2D2F35, 0);
      s.setColor(0x50FFFFFF); s.setStrokeWidth(hair);
      r.set(x - rad * .74f, y - rad * .74f, x + rad * .74f, y + rad * .74f); c.drawArc(r, 200, 130, false, s);
      dot(c, x, y, rad * .36f, 0xFF222428, 0);
    }
    void body(Canvas c, Path path, int col, int edge) {
      drawBody(c,path,shellColor==0?col:shellColor,shellColor==0?edge:mix(shellColor,0xFFFFFFFF,.35f));
    }
    void drawBody(Canvas c, Path path, int col, int edge) {
      RectF bb = new RectF(); path.computeBounds(bb, true);
      p.setColor(0xFFFFFFFF);
      p.setShader(new LinearGradient(0, bb.top, 0, bb.bottom, mix(col, 0xFFFFFFFF, .1f), mix(col, 0xFF000000, .12f), Shader.TileMode.CLAMP));
      c.drawPath(path, p); p.setShader(null);
      c.save(); c.clipPath(path); c.translate(0, hair * 1.4f);
      s.setColor(0x33FFFFFF); s.setStrokeWidth(hair * 1.4f); c.drawPath(path, s); c.restore();
      s.setColor(edge); s.setStrokeWidth(hair); c.drawPath(path, s);
    }

    void joy(Canvas c, boolean left, int col) {
      float R = 26, q = 5;
      Path b = new Path();
      b.addRoundRect(new RectF(0, 0, 52, 140), left ? new float[]{R, R, q, q, q, q, R, R} : new float[]{q, q, R, R, R, R, q, q}, Path.Direction.CW);
      drawBody(c, b, col, mix(col, 0xFFFFFFFF, .35f));
      s.setColor(mix(col, 0xFF000000, .35f)); s.setStrokeWidth(3.2f);
      r.set(2.5f, 2.5f, 49.5f, 49.5f); c.drawArc(r, left ? 188 : 272, 80, false, s);
      rr(c, left ? 49 : 0, 5, left ? 52 : 3, 135, 1, K);
      if (left) {
        rr(c, 36, 11, 45, 14, 1.2f, K); stick(c, 26, 40, 10.5f);
        diamond(c, 26, 82, 10, 4.4f, K, K, K, K, 0); rr(c, 29, 108, 37, 116, 1.6f, K);
      } else {
        rr(c, 8, 11.3f, 17, 13.7f, 1, K); rr(c, 11.3f, 8, 13.7f, 17, 1, K);
        diamond(c, 26, 40, 10, 4.4f, K, K, K, K, 0); stick(c, 26, 82, 10.5f);
        dot(c, 21, 112, 4.6f, K, mix(col, 0xFFFFFFFF, .4f));
      }
    }

    void pair(Canvas c) {
      drawBody(c, union(rrp(30, 10, 120, 100, 12), ov(14, 66, 64, 148), ov(86, 66, 136, 148)), 0xFF2B2D33, 0xFF4C4F57);
      rr(c, 68, 50, 82, 52, 1, 0xFF1C1D21);
      float h0 = hair;
      hair = h0 / .7f;
      c.save(); c.translate(20, 6); c.scale(.7f, .7f); joy(c, true, shellColor==0?BLUE:shellColor); c.restore();
      c.save(); c.translate(93.6f, 6); c.scale(.7f, .7f); joy(c, false, rightColor==0?RED:rightColor); c.restore();
      hair = h0;
    }

    void pro(Canvas c) {
      rr(c, 22, 3, 62, 14, 5, 0xFF1A1B1F); rr(c, 98, 3, 138, 14, 5, 0xFF1A1B1F);
      body(c, union(rrp(18, 8, 142, 64, 24), ov(6, 28, 66, 108), ov(94, 28, 154, 108)), 0xFF2E3036, 0xFF50535B);
      int e = 0xFF4A4D55, kk = 0xFF0F1012;
      stick(c, 44, 36, 10.5f); dpad(c, 62, 60, 18, K); stick(c, 98, 60, 10.5f);
      diamond(c, 118, 36, 9.5f, 4.3f, K, K, K, K, e);
      rr(c, 64, 20.5f, 71, 23, 1, kk);
      rr(c, 89, 20.5f, 96, 23, 1, kk); rr(c, 91.25f, 18.25f, 93.75f, 25.25f, 1, kk);
      rr(c, 68, 29, 74, 35, 1.4f, kk); dot(c, 92, 32, 3.4f, kk, e);
    }

    void steam(Canvas c) {
      int shell = 0xFF2A2C31, edge = 0xFF55585F, well = 0xFF141518, wellEdge = 0xFF41444B;
      rr(c, 26, 3, 58, 14, 5, 0xFF1A1B1E);    // triggers
      rr(c, 102, 3, 134, 14, 5, 0xFF1A1B1E);
      rr(c, 22, 7, 62, 19, 6, 0xFF3A3D43);    // bumpers
      rr(c, 98, 7, 138, 19, 6, 0xFF3A3D43);
      Path b = new Path();
      b.moveTo(30, 14); b.cubicTo(55, 6, 105, 6, 130, 14);
      b.cubicTo(150, 20, 158, 50, 156, 78); b.cubicTo(155, 100, 146, 112, 134, 110);
      b.cubicTo(122, 108, 114, 88, 104, 84); b.cubicTo(92, 80, 68, 80, 56, 84);
      b.cubicTo(46, 88, 38, 108, 26, 110); b.cubicTo(14, 112, 5, 100, 4, 78);
      b.cubicTo(2, 50, 10, 20, 30, 14); b.close();
      body(c, b, shell, edge);
      // trackpads: recessed well, raised rim, soft top highlight
      float[] px = {44, 116};
      for (float x : px) {
        dot(c, x, 40, 18.5f, well, wellEdge);
        dot(c, x, 40, 16.2f, 0xFF25272C, 0xFF3A3D44);
        s.setColor(0x40FFFFFF); s.setStrokeWidth(hair);
        r.set(x - 16.2f, 23.8f, x + 16.2f, 56.2f); c.drawArc(r, 205, 130, false, s);
      }
      dpad(c, 44, 40, 21, 0xFF34373D);        // etched cross, left pad only
      s.setColor(0x22FFFFFF); s.setStrokeWidth(hair);
      c.drawCircle(116, 40, 9.5f, s); c.drawCircle(116, 40, 4.5f, s);
      // single lower-left stick
      dot(c, 62, 70, 10.5f, well, wellEdge); stick(c, 62, 70, 8.6f);
      // tight ABXY: dark caps with colored rings and pips (Y top, B right, A bottom, X left)
      float ax = 98, ay = 70, sp = 6.5f, br = 3.4f;
      int[] cols = {0xFFE8BC1E, 0xFFE0474C, 0xFF4BB662, 0xFF3D7FEA};
      float[][] o = {{0, -sp}, {sp, 0}, {0, sp}, {-sp, 0}};
      for (int j = 0; j < 4; j++) {
        dot(c, ax + o[j][0], ay + o[j][1], br, 0xFF1D1E22, cols[j]);
        dot(c, ax + o[j][0], ay + o[j][1], 1.1f, cols[j], 0);
      }
      // center cluster: small back/start, raised guide with white glyph
      dot(c, 69, 41, 2.3f, 0xFF16171A, 0xFF5A5D64);
      dot(c, 91, 41, 2.3f, 0xFF16171A, 0xFF5A5D64);
      dot(c, 80, 32, 7, well, wellEdge);
      dot(c, 80, 32, 5.4f, 0xFF2E3036, 0xFF60636A);
      s.setColor(0xFFF2F3F5); s.setStrokeWidth(hair * 1.4f);
      c.drawCircle(80, 32, 2.6f, s);
      dot(c, 80, 32, 0.9f, 0xFFF2F3F5, 0);
    }

    void nano(Canvas c) {
      int ed = 0xFF5C6068, well = 0xFF121316, wellEdge = 0xFF3C3F46, small = 0xFF6E727A;
      rr(c, 16, 6, 44, 18, 5, 0xFF24262B);   // triggers
      rr(c, 96, 6, 124, 18, 5, 0xFF24262B);
      rr(c, 10, 10, 50, 24, 7, 0xFF34363C);  // bumpers
      rr(c, 90, 10, 130, 24, 7, 0xFF34363C);
      // compact ~2:1 slab with stubby rounded lower lobes, no long grips
      Path b = union(rrp(4, 16, 136, 74, 22), ov(4, 30, 54, 84), ov(86, 30, 136, 84));
      body(c, b, 0xFF1D1E22, ed);
      // offset sticks in recessed wells
      dot(c, 34, 40, 11, well, wellEdge); stick(c, 34, 40, 8.8f);
      dot(c, 90, 60, 11, well, wellEdge); stick(c, 90, 60, 8.8f);
      // d-pad, lighter than body for contrast
      dot(c, 50, 60, 9.5f, well, wellEdge); dpad(c, 50, 60, 14, 0xFF4A4D55);
      // face buttons: Y top, B right, A bottom, X left
      diamond(c, 106, 40, 7.8f, 3.7f, 0xFFE8BC1E, 0xFFE0474C, 0xFF4BB662, 0xFF3D7FEA, 0x66000000);
      // guide (lit), view / menu / share
      dot(c, 70, 28, 5, 0xFF2C2E33, 0xFF7A7E86); dot(c, 70, 28, 2.4f, 0xFFE9EAEC, 0);
      dot(c, 62, 41, 1.9f, small, 0); dot(c, 78, 41, 1.9f, small, 0);
      rr(c, 67.5f, 46, 72.5f, 49, 1.2f, small);
      // programmable Action Button
      dot(c, 70, 62, 3.4f, 0xFF2C2E33, 0xFF9AA0AA);
    }

    void xbox(Canvas c) {
      rr(c, 30, 3, 58, 14, 5, 0xFF9DA0A7);   // triggers
      rr(c, 102, 3, 130, 14, 5, 0xFF9DA0A7);
      rr(c, 24, 7, 62, 20, 6, 0xFFC4C7CD);   // bumpers
      rr(c, 98, 7, 136, 20, 6, 0xFFC4C7CD);
      Path b = new Path();
      b.moveTo(32, 16); b.cubicTo(56, 8, 104, 8, 128, 16); b.cubicTo(146, 22, 154, 52, 156, 80); b.cubicTo(158, 100, 150, 110, 140, 108);
      b.cubicTo(128, 106, 118, 84, 104, 78); b.cubicTo(92, 74, 68, 74, 56, 78); b.cubicTo(42, 84, 32, 106, 20, 108);
      b.cubicTo(10, 110, 2, 100, 4, 80); b.cubicTo(6, 52, 14, 22, 32, 16); b.close();
      body(c, b, 0xFFE9EAED, 0xFF8F939B);
      int well = 0xFFD2D4D9, wellEdge = 0xFFA3A6AD;
      dot(c, 40, 40, 13, well, wellEdge); stick(c, 40, 40, 11);
      dot(c, 60, 62, 11.5f, well, wellEdge); dpad(c, 60, 62, 18, 0xFF1E1F23);
      dot(c, 100, 62, 11.5f, well, wellEdge); stick(c, 100, 62, 9.8f);
      diamond(c, 122, 40, 10, 5, 0xFFF0BE00, 0xFFE5484D, 0xFF45B35E, 0xFF3B82F6, 0x55000000);
      dot(c, 80, 23, 7.2f, 0xFFF6F7F8, 0xFF8F939B); dot(c, 80, 23, 4.2f, 0xFFDCDEE2, 0);
      dot(c, 68, 40, 2.6f, 0xFF3E4148, 0); dot(c, 92, 40, 2.6f, 0xFF3E4148, 0);
      rr(c, 76.5f, 46, 83.5f, 50, 1.5f, 0xFF3E4148);
    }

    void ps(Canvas c) {
      rr(c, 22, 8, 60, 20, 5, 0xFFD4D6DA); rr(c, 100, 8, 138, 20, 5, 0xFFD4D6DA);
      Path b = new Path();
      b.moveTo(30, 14); b.lineTo(130, 14); b.cubicTo(148, 16, 154, 40, 156, 70); b.cubicTo(158, 96, 152, 110, 140, 110);
      b.cubicTo(128, 110, 120, 90, 110, 82); b.lineTo(50, 82); b.cubicTo(40, 90, 32, 110, 20, 110);
      b.cubicTo(8, 110, 2, 96, 4, 70); b.cubicTo(6, 40, 12, 16, 30, 14); b.close();
      body(c, b, 0xFFEDEEF0, 0xFFA9ACB3);
      c.save(); c.clipPath(b); rr(c, 46, 44, 114, 92, 12, 0xFF1D1E22); c.restore();
      rr(c, 52, 15, 108, 42, 5, 0xFFE2E3E6);
      s.setColor(0xFFB3B6BC); s.setStrokeWidth(hair); r.set(52, 15, 108, 42); c.drawRoundRect(r, 5, 5, s);
      s.setColor(0xFF4C8DFF); s.setStrokeWidth(1.6f); c.drawLine(49.5f, 17, 49.5f, 38, s); c.drawLine(110.5f, 17, 110.5f, 38, s);
      int g = 0xFF2A2B30;
      dpad(c, 32, 42, 20, g); diamond(c, 128, 42, 10, 4.6f, g, g, g, g, 0);
      stick(c, 64, 64, 10); stick(c, 96, 64, 10);
      dot(c, 80, 62, 2.6f, 0xFF3B3D43, 0); rr(c, 76, 75, 84, 78, 1.2f, 0xFF3B3D43);
      rr(c, 40, 20, 44, 28, 1.5f, 0xFF7E8189); rr(c, 116, 20, 120, 28, 1.5f, 0xFF7E8189);
    }

    void wii(Canvas c) {
      int e = 0xFFA0A3AA;
      body(c, rrp(10, 4, 46, 156, 12), 0xFFEEEFF1, 0xFFA9ACB3);
      dot(c, 28, 14, 3.2f, 0xFFC9CCD2, e); dot(c, 28, 14, 1.1f, 0xFFE5484D, 0);
      dpad(c, 28, 37, 20, 0xFFB4B7BE);
      dot(c, 28, 63, 8, 0xFFF7F8F9, 0xFF9EA1A8);
      dot(c, 17, 92, 2.6f, 0xFFCDD0D5, e); dot(c, 28, 92, 3.6f, 0xFFCDD0D5, 0xFF5E9BEA); dot(c, 39, 92, 2.6f, 0xFFCDD0D5, e);
      for (int j = 0; j < 6; j++) dot(c, 28 + (j - 2.5f) * 3.2f, 104, 0.8f, 0xFFB1B4BA, 0);
      dot(c, 28, 121, 4.6f, 0xFFE4E6E9, e); dot(c, 28, 135, 4.6f, 0xFFE4E6E9, e);
      for (int j = 0; j < 4; j++) { float x = 28 + (j - 1.5f) * 5.5f; rr(c, x - 1.3f, 147, x + 1.3f, 149, 0.6f, j == 0 ? 0xFF4FA8FF : 0xFFA9C8EA); }
    }

    void thor(Canvas c) {
      int sh = 0xFF2B2D33, ed = 0xFF50535B, scr = 0xFF07080A;
      body(c, rrp(6, 4, 144, 88, 10), sh, ed);
      rr(c, 14, 12, 136, 80, 4, scr);
      Path gl = new Path(); gl.moveTo(98, 12); gl.lineTo(122, 12); gl.lineTo(96, 80); gl.lineTo(72, 80); gl.close();
      p.setShader(null); p.setColor(0x12FFFFFF); c.drawPath(gl, p);
      rr(c, 18, 86, 132, 95, 3, 0xFF18191C);
      body(c, rrp(6, 93, 144, 174, 12), sh, ed);
      rr(c, 43, 101, 107, 157, 3, scr);
      for (int j = 0; j < 4; j++) { float l = 47.5f + j * 14.5f; rr(c, l, 121, l + 12, 137, 2, mix(SEAT[j], scr, .35f)); }
      stick(c, 25, 110, 9); dpad(c, 25, 140, 18, K);
      diamond(c, 125, 110, 7.5f, 3.4f, K, K, K, K, 0xFF4A4D55); stick(c, 125, 140, 9);
      rr(c, 60, 163, 68, 166, 1.5f, K); rr(c, 82, 163, 90, 166, 1.5f, K);
    }

    void generic(Canvas c) {
      rr(c, 18, 10, 52, 22, 5, 0xFF2E3036); rr(c, 98, 10, 132, 22, 5, 0xFF2E3036);
      body(c, union(ov(4, 16, 74, 96), ov(76, 16, 146, 96), rrp(40, 22, 110, 80, 14)), 0xFF4A4D55, 0xFF6E727B);
      dpad(c, 38, 56, 22, 0xFF1C1D21);
      int l = 0xFFC9CCD2;
      diamond(c, 112, 56, 10, 4.8f, l, l, l, l, 0x40000000);
      rr(c, 63, 57, 72, 61, 2, 0xFF26282D); rr(c, 78, 57, 87, 61, 2, 0xFF26282D);
    }

    void empty(Canvas c) {
      Path b = union(ov(4, 16, 74, 96), ov(76, 16, 146, 96), rrp(40, 22, 110, 80, 14));
      s.setColor(accent); s.setStrokeWidth(hair * 1.3f);
      s.setPathEffect(new DashPathEffect(new float[]{hair * 5, hair * 4}, 0));
      c.drawPath(b, s);
      s.setPathEffect(null);
      s.setColor(MUTED); s.setStrokeWidth(hair * 2f);
      c.drawLine(75, 46, 75, 66, s); c.drawLine(65, 56, 85, 56, s);
    }
  }

  static String hw(Card k) {
    if (isEmpty(k)) return "empty";
    String h = k.hardware == null ? "generic" : k.hardware.trim().toLowerCase(Locale.ROOT);
    return KNOWN.contains(" " + h + " ") ? h : "generic";
  }

  static boolean isEmpty(Card k) {
    return k == null || k.title == null || k.title.trim().isEmpty() || "Open slot".equalsIgnoreCase(k.title.trim())
        || (k.hardware != null && "empty".equalsIgnoreCase(k.hardware.trim()));
  }

  static Card at(Card[] cards, int i) { return cards != null && i < cards.length ? cards[i] : null; }

  static LinearLayout lin(Context c, int o) { LinearLayout l = new LinearLayout(c); l.setOrientation(o); return l; }

  static View rule(Context c) { View v = new View(c); v.setBackgroundColor(LINE); return v; }

  static TextView text(Context c, String s, float sp, int color, boolean strong) {
    TextView t = new TextView(c);
    t.setText(s);
    t.setTextColor(color);
    t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
    t.setTypeface(Typeface.create(strong ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
    t.setIncludeFontPadding(false);
    return t;
  }

  static int hair(Context c) { return Math.max(1, Math.round(c.getResources().getDisplayMetrics().density * 0.5f)); }

  static int dp(Context c, float v) { return Math.round(v * c.getResources().getDisplayMetrics().density); }

  static int mix(int a, int b, float t) {
    return Color.argb(Color.alpha(a),
        (int) (Color.red(a) + (Color.red(b) - Color.red(a)) * t),
        (int) (Color.green(a) + (Color.green(b) - Color.green(a)) * t),
        (int) (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t));
  }
}