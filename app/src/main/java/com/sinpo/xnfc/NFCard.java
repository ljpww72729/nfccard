/* NFCard is free software; you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation; either version 3 of the License, or
(at your option) any later version.

NFCard is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with Wget.  If not, see <http://www.gnu.org/licenses/>.

Additional permission under GNU GPL version 3 section 7 */

package com.sinpo.xnfc;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.nfc.NfcAdapter;
import android.os.Bundle;
import android.os.Parcelable;
import android.text.ClipboardManager;
import android.text.Editable;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.TextView;
import android.widget.Toast;

import com.sinpo.xnfc.card.CardManager;

import org.xml.sax.XMLReader;

import java.util.Arrays;

import skyseraph.android.util.LogUtil;
import skyseraph.android.util.MyConstant;

public final class NFCard extends Activity implements OnClickListener, Html.ImageGetter, Html.TagHandler
{
	private static final String TAG_ASSIST = "[NFCard]-";
	private NfcAdapter nfcAdapter;
	private PendingIntent pendingIntent;
	private Resources res;
	private TextView board;

	private enum ContentType
	{
		HINT, DATA, MSG
	}

	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.nfcard);

		LogUtil.d(MyConstant.TAG, TAG_ASSIST+"onCreate");
		
		final Resources res = getResources();
		this.res = res;

		final View decor = getWindow().getDecorView();// 最顶层
		final TextView board = (TextView) decor.findViewById(R.id.board);
		this.board = board; // zhaob: 一种编程思维

		decor.findViewById(R.id.btnCopy).setOnClickListener(this);
		decor.findViewById(R.id.btnNfc).setOnClickListener(this);
		decor.findViewById(R.id.btnExit).setOnClickListener(this);

		board.setMovementMethod(LinkMovementMethod.getInstance());// 滚动效果
		board.setFocusable(false);
		board.setClickable(false);
		board.setLongClickable(false);

		nfcAdapter = NfcAdapter.getDefaultAdapter(this);
		pendingIntent = PendingIntent.getActivity(this, 0,
				new Intent(this, getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0);

		onNewIntent(getIntent());
	}

	@Override
	protected void onPause()
	{
		super.onPause();
		LogUtil.d(MyConstant.TAG, TAG_ASSIST+"onPause");
		if (nfcAdapter != null)
			nfcAdapter.disableForegroundDispatch(this);
	}

	@Override
	protected void onResume()
	{
		super.onResume();
		LogUtil.d(MyConstant.TAG, TAG_ASSIST+"onResume");
		if (nfcAdapter != null)
			nfcAdapter.enableForegroundDispatch(this, pendingIntent, CardManager.FILTERS, CardManager.TECHLISTS);

		refreshStatus();
	}

	@Override
	protected void onNewIntent(Intent intent)
	{
		LogUtil.d(MyConstant.TAG, TAG_ASSIST+"onNewIntent");
		final Parcelable p = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
		showData((p != null) ? CardManager.load(p, res) : null);
	}

	@Override
	public void onClick(final View v)
	{
		switch (v.getId())
		{
		case R.id.btnCopy:
		{
			copyData();
			break;
		}
		case R.id.btnNfc:
		{
			startActivityForResult(new Intent(android.provider.Settings.ACTION_WIRELESS_SETTINGS), 0);
			break;
		}
		case R.id.btnExit:
		{
			finish();
			break;
		}
		default:
			break;
		}
	}

	protected void onActivityResult(int requestCode, int resultCode, Intent data)
	{
		LogUtil.d(MyConstant.TAG, TAG_ASSIST+"onActivityResult");
		refreshStatus();
	}

	private void refreshStatus()
	{
		LogUtil.d(MyConstant.TAG, TAG_ASSIST+"refreshStatus");
		final Resources r = this.res;

		final String tip;
		if (nfcAdapter == null)
			tip = r.getString(R.string.tip_nfc_notfound);
		else if (nfcAdapter.isEnabled())
			tip = r.getString(R.string.tip_nfc_enabled);
		else
			tip = r.getString(R.string.tip_nfc_disabled);

		final StringBuilder s = new StringBuilder(r.getString(R.string.app_name));

		s.append("  --  ").append(tip);
		setTitle(s);

		final CharSequence text = board.getText();
		if (text == null || board.getTag() == ContentType.HINT)
			showHint();
	}

	private void copyData()
	{
		LogUtil.d(MyConstant.TAG, TAG_ASSIST+"copyData");
		final CharSequence text = board.getText();
		if (text == null || board.getTag() != ContentType.DATA)
			return;

		((ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE)).setText(text);

		final String msg = res.getString(R.string.msg_copied);
		final Toast toast = Toast.makeText(this, msg, Toast.LENGTH_SHORT);
		toast.setGravity(Gravity.CENTER, 0, 0);
		toast.show();
	}

	@Override
	public void handleTag(boolean opening, String tag, Editable output, XMLReader xmlReader)
	{
		if (!opening && "version".equals(tag))
		{
			LogUtil.d(MyConstant.TAG, TAG_ASSIST+"handleTag");
			try
			{
				output.append(getPackageManager().getPackageInfo(getPackageName(), 0).versionName);
			} catch (NameNotFoundException e)
			{
			}
		}
	}

	private Drawable spliter;
	@Override
	public Drawable getDrawable(String source)
	{
		LogUtil.d(MyConstant.TAG, TAG_ASSIST+"getDrawable");
		final Resources res = this.res;

		final Drawable ret;
		if (source.startsWith("spliter"))
		{
			LogUtil.d(MyConstant.TAG, TAG_ASSIST+"getDrawable,spliter="+spliter);
			if (spliter == null)
			{
				final int w = res.getDisplayMetrics().widthPixels;
				final int h = (int) (res.getDisplayMetrics().densityDpi / 72f + 0.5f);

				final int[] pix = new int[w * h];
				Arrays.fill(pix, res.getColor(R.color.bg_default));
				spliter = new BitmapDrawable(Bitmap.createBitmap(pix, w, h, Bitmap.Config.ARGB_8888));
				spliter.setBounds(0, 3 * h, w, 4 * h);
			}
			ret = spliter;

		} else if (source.startsWith("icon_main"))
		{
			LogUtil.d(MyConstant.TAG, TAG_ASSIST+"getDrawable,icon_main");
			ret = res.getDrawable(R.drawable.ic_app_main);

			final String[] params = source.split(",");
			final float f = res.getDisplayMetrics().densityDpi / 72f;
			final float w = Util.parseInt(params[1], 10, 16) * f + 0.5f;
			final float h = Util.parseInt(params[2], 10, 16) * f + 0.5f;
			ret.setBounds(0, 0, (int) w, (int) h);

		} else
		{
			ret = null;
		}

		return ret;
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		LogUtil.d(MyConstant.TAG, TAG_ASSIST+"onOptionsItemSelected");
		switch (item.getItemId())
		{
		case R.id.clear:
			showData(null);
			return true;
		case R.id.help:
			showHelp(R.string.info_help);
			return true;
		case R.id.about:
			showHelp(R.string.info_about);
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}
	
	private void showData(String data)
	{
		LogUtil.d(MyConstant.TAG, TAG_ASSIST+"showData，data="+data);
		if (data == null || data.length() == 0)
		{
			showHint();
			return;
		}

		final TextView board = this.board;
		final Resources res = this.res;

		final int padding = res.getDimensionPixelSize(R.dimen.pnl_margin);

		board.setPadding(padding, padding, padding, padding);
		board.setTypeface(Typeface.defaultFromStyle(Typeface.NORMAL));
		board.setTextSize(res.getDimension(R.dimen.text_small));
		board.setTextColor(res.getColor(R.color.text_default));
		board.setGravity(Gravity.NO_GRAVITY);
		board.setTag(ContentType.DATA);
		board.setText(Html.fromHtml(data, this, null));
		LogUtil.d(MyConstant.TAG, TAG_ASSIST+"showData-out");
	}

	private void showHelp(int id)
	{
		LogUtil.d(MyConstant.TAG, TAG_ASSIST+"showHelp");
		final TextView board = this.board;
		final Resources res = this.res;

		final int padding = res.getDimensionPixelSize(R.dimen.pnl_margin);

		board.setPadding(padding, padding, padding, padding);
		board.setTypeface(Typeface.defaultFromStyle(Typeface.NORMAL));
		board.setTextSize(res.getDimension(R.dimen.text_small));
		board.setTextColor(res.getColor(R.color.text_default));
		board.setGravity(Gravity.NO_GRAVITY);
		board.setTag(ContentType.MSG);
		board.setText(Html.fromHtml(res.getString(id), this, this));
	}
	
	private void showHint()
	{
		LogUtil.d(MyConstant.TAG, TAG_ASSIST+"showHint");
		final TextView board = this.board;
		final Resources res = this.res;
		final String hint;

		if (nfcAdapter == null)
			hint = res.getString(R.string.msg_nonfc);
		else if (nfcAdapter.isEnabled())
			hint = res.getString(R.string.msg_nocard);
		else
			hint = res.getString(R.string.msg_nfcdisabled);

		final int padding = res.getDimensionPixelSize(R.dimen.text_middle);

		board.setPadding(padding, padding, padding, padding);
		board.setTypeface(Typeface.defaultFromStyle(Typeface.BOLD_ITALIC));
		board.setTextSize(res.getDimension(R.dimen.text_middle));
		board.setTextColor(res.getColor(R.color.text_tip));
		board.setGravity(Gravity.CENTER_VERTICAL);
		board.setTag(ContentType.HINT);
		board.setText(Html.fromHtml(hint));
	}
}
