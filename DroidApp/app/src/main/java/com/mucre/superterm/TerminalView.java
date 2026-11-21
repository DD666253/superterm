package com.mucre.superterm;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

/** Minimal but real ANSI/VT terminal: character grid, SGR, cursor addressing, scroll region. */
public class TerminalView extends View {

    public interface Listener {
        void onTextInput(String text);
        void onKeyCode(int keyCode, boolean ctrl, boolean alt);
        void onResize(int rows, int cols);
    }

    private Listener listener;

    private int cols = 80;
    private int rows = 24;

    private char[] text;
    private int[] fg;
    private int[] bg;
    private boolean[] bold;
    private boolean[] underline;

    private int cursorRow = 0;
    private int cursorCol = 0;
    private boolean cursorVisible = true;
    private int savedRow = 0;
    private int savedCol = 0;
    private int scrollTop = 0;
    private int scrollBottom = 23;

    private int curFg = 7;
    private int curBg = -1;
    private boolean curBold = false;
    private boolean curUnderline = false;
    private boolean curReverse = false;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bgPaint = new Paint();
    private float charW = 10f;
    private float charH = 20f;
    private int pad = 6;

    private static final int[] PALETTE = new int[]{
            0xFF0B0F14, 0xFFCD3131, 0xFF0DBC79, 0xFFE5E510, 0xFF2472C8, 0xFFBC3FBC, 0xFF11A8CD, 0xFFE5E5E5,
            0xFF666666, 0xFFF14C4C, 0xFF23D18B, 0xFFF5F543, 0xFF3B8EEA, 0xFFD670D6, 0xFF29B8DB, 0xFFFFFFFF
    };

    public TerminalView(Context c) {
        super(c);
        setFocusable(true);
        setFocusableInTouchMode(true);
        paint.setTypeface(Typeface.MONOSPACE);
        paint.setTextSize(34f);
        charW = paint.measureText("M");
        Paint.FontMetrics fm = paint.getFontMetrics();
        charH = fm.bottom - fm.top;
        allocate(rows, cols);
    }

    public void setListener(Listener l) { listener = l; }

    private void allocate(int r, int c) {
        rows = r; cols = c;
        int n = rows * cols;
        text = new char[n]; fg = new int[n]; bg = new int[n];
        bold = new boolean[n]; underline = new boolean[n];
        for (int i = 0; i < n; i++) { text[i] = ' '; fg[i] = 7; bg[i] = -1; }
        scrollTop = 0; scrollBottom = rows - 1;
        cursorRow = Math.min(cursorRow, rows - 1);
        cursorCol = Math.min(cursorCol, cols - 1);
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        if (charW <= 0) charW = 10;
        if (charH <= 0) charH = 20;
        int newCols = Math.max(20, (int) ((w - 2 * pad) / charW));
        int newRows = Math.max(6, (int) (h / charH));
        if (newCols == cols && newRows == rows) return;
        String[] keep = new String[rows];
        for (int r = 0; r < rows; r++) keep[r] = rowText(r);
        allocate(newRows, newCols);
        int offset = Math.max(0, rows - keep.length);
        for (int r = 0; r < Math.min(newRows, keep.length); r++) {
            String line = keep[Math.max(0, keep.length - newRows + r)];
            for (int c = 0; c < Math.min(cols, line.length()); c++) text[r * cols + c] = line.charAt(c);
        }
        cursorRow = newRows - 1;
        cursorCol = 0;
        if (listener != null) listener.onResize(rows, cols);
    }

    private String rowText(int r) {
        StringBuilder sb = new StringBuilder();
        for (int c = 0; c < cols; c++) sb.append(text[r * cols + c]);
        int end = sb.length();
        while (end > 0 && sb.charAt(end - 1) == ' ') end--;
        return sb.substring(0, end);
    }

    private int colorOf(int idx) {
        if (idx <= -1000) return -(idx + 1000);
        if (idx < 0) return Color.WHITE;
        if (idx < 16) return PALETTE[idx];
        if (idx < 232) {
            int n = idx - 16;
            int r = n / 36, g = (n % 36) / 6, b = n % 6;
            int rv = r == 0 ? 0 : 55 + r * 40;
            int gv = g == 0 ? 0 : 55 + g * 40;
            int bv = b == 0 ? 0 : 55 + b * 40;
            return Color.rgb(rv, gv, bv);
        }
        int gray = 8 + (idx - 232) * 10;
        return Color.rgb(gray, gray, gray);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawColor(Color.parseColor("#0B0F14"));
        Paint.FontMetrics fm = paint.getFontMetrics();
        float baseY = -fm.top;
        int n = rows * cols;
        for (int i = 0; i < n; i++) {
            int r = i / cols, c = i % cols;
            float x = pad + c * charW;
            float y = r * charH;
            if (bg[i] >= 0) {
                bgPaint.setColor(colorOf(bg[i]));
                canvas.drawRect(x, y, x + charW, y + charH, bgPaint);
            }
            char ch = text[i];
            if (ch == ' ') continue;
            paint.setColor(colorOf(fg[i]));
            paint.setFakeBoldText(bold[i]);
            canvas.drawText(String.valueOf(ch), x, y + baseY, paint);
            paint.setFakeBoldText(false);
            if (underline[i]) {
                bgPaint.setColor(colorOf(fg[i]));
                canvas.drawRect(x, y + charH - 3, x + charW, y + charH - 2, bgPaint);
            }
        }
        if (cursorVisible && hasFocus()) {
            float x = pad + cursorCol * charW;
            float y = cursorRow * charH;
            bgPaint.setColor(Color.parseColor("#3B8EEA"));
            canvas.drawRect(x, y, x + charW, y + charH, bgPaint);
            char cc = text[cursorRow * cols + cursorCol];
            if (cc != ' ') {
                paint.setColor(Color.parseColor("#0B0F14"));
                canvas.drawText(String.valueOf(cc), x, y + baseY, paint);
            }
        }
    }

    // ---------------- parser ----------------
    private static final int ST_NORMAL = 0, ST_ESC = 1, ST_CSI = 2, ST_OSC = 3, ST_CHARSET = 4;
    private int state = ST_NORMAL;
    private final StringBuilder csi = new StringBuilder();

    public void feed(byte[] data, int len) {
        for (int i = 0; i < len; i++) handle((char) (data[i] & 0xFF));
        postInvalidate();
    }

    private void handle(char ch) {
        switch (state) {
            case ST_NORMAL:
                if (ch == 0x1b) { state = ST_ESC; return; }
                if (ch == '\r') { cursorCol = 0; return; }
                if (ch == '\n') { lineFeed(); return; }
                if (ch == '\b') { if (cursorCol > 0) cursorCol--; return; }
                if (ch == '\t') { cursorCol = Math.min(cols - 1, (cursorCol / 8 + 1) * 8); return; }
                if (ch == 7 || ch < 32) return;
                putChar(ch);
                return;
            case ST_ESC:
                if (ch == '[') { state = ST_CSI; csi.setLength(0); return; }
                if (ch == ']') { state = ST_OSC; return; }
                if (ch == '(' || ch == ')' || ch == '*' || ch == '+') { state = ST_CHARSET; return; }
                if (ch == '7') { savedRow = cursorRow; savedCol = cursorCol; state = ST_NORMAL; return; }
                if (ch == '8') { cursorRow = savedRow; cursorCol = savedCol; state = ST_NORMAL; return; }
                if (ch == 'c') { reset(); state = ST_NORMAL; return; }
                if (ch == 'M') { reverseIndex(); state = ST_NORMAL; return; }
                state = ST_NORMAL; return;
            case ST_CHARSET:
                state = ST_NORMAL; return;
            case ST_OSC:
                if (ch == 7 || ch == 0x1b) state = ST_NORMAL; return;
            case ST_CSI:
                if (ch >= 0x40 && ch <= 0x7e) {
                    applyCsi(ch, csi.toString());
                    state = ST_NORMAL; csi.setLength(0); return;
                }
                csi.append(ch); return;
        }
    }

    private int atoi(String s) {
        if (s == null || s.isEmpty()) return 0;
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= '0' && c <= '9') b.append(c);
            else break;
        }
        if (b.length() == 0) return 0;
        try { return Integer.parseInt(b.toString()); } catch (Exception e) { return 0; }
    }

    private void applyCsi(char f, String param) {
        int p1 = atoi(param);
        switch (f) {
            case 'A': cursorRow = Math.max(scrollTop, cursorRow - Math.max(1, p1)); break;
            case 'B': cursorRow = Math.min(scrollBottom, cursorRow + Math.max(1, p1)); break;
            case 'C': cursorCol = Math.min(cols - 1, cursorCol + Math.max(1, p1)); break;
            case 'D': cursorCol = Math.max(0, cursorCol - Math.max(1, p1)); break;
            case 'E': cursorRow = Math.min(scrollBottom, cursorRow + Math.max(1, p1)); cursorCol = 0; break;
            case 'F': cursorRow = Math.max(scrollTop, cursorRow - Math.max(1, p1)); cursorCol = 0; break;
            case 'G': cursorCol = Math.min(cols - 1, Math.max(0, p1 - 1)); break;
            case 'd': cursorRow = Math.min(rows - 1, Math.max(0, p1 - 1)); break;
            case 'H': case 'f': {
                String[] parts = param.split(";");
                int r = parts.length > 0 ? atoi(parts[0]) : 1;
                int c = parts.length > 1 ? atoi(parts[1]) : 1;
                cursorRow = Math.min(rows - 1, Math.max(0, (r == 0 ? 1 : r) - 1));
                cursorCol = Math.min(cols - 1, Math.max(0, (c == 0 ? 1 : c) - 1));
                break;
            }
            case 'J': eraseDisplay(p1); break;
            case 'K': eraseLine(p1); break;
            case 'm': sgr(param); break;
            case 'r': {
                String[] parts = param.split(";");
                int top = parts.length > 0 ? atoi(parts[0]) : 1;
                int bot = parts.length > 1 ? atoi(parts[1]) : rows;
                scrollTop = Math.max(0, (top == 0 ? 1 : top) - 1);
                scrollBottom = Math.min(rows - 1, (bot == 0 ? rows : bot) - 1);
                cursorRow = 0; cursorCol = 0;
                break;
            }
            case 'h': case 'l': {
                if (param.contains("1049") || param.contains("1047") || param.contains("47")) clearAll();
                if (param.contains("25")) cursorVisible = f == 'h';
                break;
            }
            case 's': savedRow = cursorRow; savedCol = cursorCol; break;
            case 'u': cursorRow = savedRow; cursorCol = savedCol; break;
            case 'P': {
                int n = Math.max(1, p1), base = cursorRow * cols;
                for (int c = cursorCol; c < cols; c++) text[base + c] = (c + n < cols) ? text[base + c + n] : ' ';
                break;
            }
            case '@': {
                int n = Math.max(1, p1), base = cursorRow * cols;
                for (int c = cols - 1; c >= cursorCol + n; c--) text[base + c] = text[base + c - n];
                for (int c = cursorCol; c < Math.min(cols, cursorCol + n); c++) text[base + c] = ' ';
                break;
            }
            case 'X': {
                int n = Math.max(1, p1);
                for (int c = cursorCol; c < Math.min(cols, cursorCol + n); c++) text[cursorRow * cols + c] = ' ';
                break;
            }
            case 'L': for (int k = 0; k < Math.max(1, p1); k++) scrollUpRegion(cursorRow); break;
            case 'M': for (int k = 0; k < Math.max(1, p1); k++) scrollDownRegion(cursorRow); break;
            default: break;
        }
    }
