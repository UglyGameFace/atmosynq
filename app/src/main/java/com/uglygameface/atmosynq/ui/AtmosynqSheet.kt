package com.uglygameface.atmosynq.ui

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

object AtmosynqSheet {
    data class Action(
        val title: String,
        val detail: String? = null,
        val onClick: () -> Unit
    )

    fun showMenu(
        activity: Activity,
        title: String,
        subtitle: String? = null,
        actions: List<Action>
    ) {
        val dialog = createDialog(activity)
        val root = sheetRoot(activity)
        addHeader(activity, root, title, subtitle)

        actions.forEachIndexed { index, action ->
            root.addView(
                actionRow(
                    activity = activity,
                    title = action.title,
                    detail = action.detail,
                    emphasized = index == 0
                ) {
                    dialog.dismiss()
                    action.onClick()
                },
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = dp(activity, 8)
                }
            )
        }

        root.addView(
            dismissRow(activity, "CLOSE") {
                dialog.dismiss()
            }
        )

        present(dialog, root)
    }

    fun showMessage(
        activity: Activity,
        title: String,
        message: String
    ) {
        val dialog = createDialog(activity)
        val root = sheetRoot(activity)
        addHeader(activity, root, title, null)

        root.addView(
            TextView(activity).apply {
                text = message
                textSize = 14f
                setTextColor(Color.rgb(202, 224, 238))
                setLineSpacing(0f, 1.16f)
                setPadding(
                    dp(activity, 4),
                    dp(activity, 2),
                    dp(activity, 4),
                    dp(activity, 18)
                )
            }
        )

        root.addView(
            dismissRow(activity, "DONE") {
                dialog.dismiss()
            }
        )

        present(dialog, root)
    }

    fun showTextInput(
        activity: Activity,
        title: String,
        subtitle: String,
        hint: String,
        onSubmit: (String) -> Unit
    ) {
        val dialog = createDialog(activity)
        val root = sheetRoot(activity)
        addHeader(activity, root, title, subtitle)

        val input =
            EditText(activity).apply {
                this.hint = hint
                textSize = 16f
                setSingleLine(true)
                setTextColor(Color.WHITE)
                setHintTextColor(Color.rgb(115, 151, 176))
                setPadding(
                    dp(activity, 16),
                    dp(activity, 14),
                    dp(activity, 16),
                    dp(activity, 14)
                )
                background =
                    rounded(
                        activity,
                        Color.rgb(5, 24, 42),
                        18,
                        Color.rgb(45, 91, 120)
                    )
            }

        root.addView(
            input,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(activity, 10)
            }
        )

        val submit =
            actionRow(
                activity = activity,
                title = "SEARCH",
                detail = "City, region, country, ZIP or postal code",
                emphasized = true
            ) {
                val value = input.text?.toString()?.trim().orEmpty()
                if (value.length < 2) {
                    input.error = "Enter at least 2 characters"
                    return@actionRow
                }

                dialog.dismiss()
                onSubmit(value)
            }

        root.addView(
            submit,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(activity, 8)
            }
        )

        root.addView(
            dismissRow(activity, "CANCEL") {
                dialog.dismiss()
            }
        )

        present(dialog, root)

        input.requestFocus()
        input.postDelayed(
            {
                activity.getSystemService(InputMethodManager::class.java)
                    ?.showSoftInput(
                        input,
                        InputMethodManager.SHOW_IMPLICIT
                    )
            },
            180L
        )
    }

    fun showList(
        activity: Activity,
        title: String,
        subtitle: String? = null,
        items: List<String>,
        onSelected: (Int) -> Unit
    ) {
        val dialog = createDialog(activity)
        val root = sheetRoot(activity)
        addHeader(activity, root, title, subtitle)

        val list =
            LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
            }

        items.forEachIndexed { index, label ->
            list.addView(
                actionRow(
                    activity = activity,
                    title = label,
                    detail = null,
                    emphasized = index == 0
                ) {
                    dialog.dismiss()
                    onSelected(index)
                },
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = dp(activity, 7)
                }
            )
        }

        val scroll =
            ScrollView(activity).apply {
                overScrollMode = View.OVER_SCROLL_NEVER
                isVerticalScrollBarEnabled = false
                addView(
                    list,
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                )
            }

        root.addView(
            scroll,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(activity, 430)
            ).apply {
                bottomMargin = dp(activity, 8)
            }
        )

        root.addView(
            dismissRow(activity, "CANCEL") {
                dialog.dismiss()
            }
        )

        present(dialog, root)
    }

    private fun createDialog(
        activity: Activity
    ): Dialog =
        Dialog(activity).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setCancelable(true)
        }

    private fun sheetRoot(
        activity: Activity
    ): LinearLayout =
        LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                dp(activity, 18),
                dp(activity, 12),
                dp(activity, 18),
                dp(activity, 18)
            )
            background =
                GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    intArrayOf(
                        Color.rgb(9, 34, 56),
                        Color.rgb(5, 21, 38),
                        Color.rgb(3, 13, 25)
                    )
                ).apply {
                    cornerRadii =
                        floatArrayOf(
                            dp(activity, 30).toFloat(),
                            dp(activity, 30).toFloat(),
                            dp(activity, 30).toFloat(),
                            dp(activity, 30).toFloat(),
                            0f,
                            0f,
                            0f,
                            0f
                        )
                    setStroke(
                        dp(activity, 1),
                        Color.rgb(40, 86, 115)
                    )
                }

            addView(
                View(activity).apply {
                    background =
                        rounded(
                            activity,
                            Color.rgb(104, 144, 169),
                            3
                        )
                },
                LinearLayout.LayoutParams(
                    dp(activity, 46),
                    dp(activity, 4)
                ).apply {
                    gravity = Gravity.CENTER_HORIZONTAL
                    bottomMargin = dp(activity, 14)
                }
            )
        }

    private fun addHeader(
        activity: Activity,
        root: LinearLayout,
        title: String,
        subtitle: String?
    ) {
        root.addView(
            TextView(activity).apply {
                text = "ATMOSYNQ  //  CONTROL"
                textSize = 9f
                letterSpacing = 0.13f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(Color.rgb(73, 216, 255))
            }
        )

        root.addView(
            TextView(activity).apply {
                text = title
                textSize = 25f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(Color.WHITE)
                setPadding(0, dp(activity, 3), 0, 0)
            }
        )

        if (!subtitle.isNullOrBlank()) {
            root.addView(
                TextView(activity).apply {
                    text = subtitle
                    textSize = 13f
                    setTextColor(Color.rgb(151, 182, 203))
                    setLineSpacing(0f, 1.12f)
                    setPadding(
                        0,
                        dp(activity, 6),
                        0,
                        dp(activity, 14)
                    )
                }
            )
        } else {
            root.addView(
                View(activity),
                LinearLayout.LayoutParams(
                    1,
                    dp(activity, 12)
                )
            )
        }
    }

    private fun actionRow(
        activity: Activity,
        title: String,
        detail: String?,
        emphasized: Boolean,
        onClick: () -> Unit
    ): LinearLayout =
        LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(
                dp(activity, 15),
                dp(activity, 13),
                dp(activity, 15),
                dp(activity, 13)
            )
            background =
                if (emphasized) {
                    gradient(
                        activity,
                        intArrayOf(
                            Color.rgb(14, 111, 156),
                            Color.rgb(31, 74, 161),
                            Color.rgb(92, 59, 184)
                        ),
                        18,
                        Color.rgb(83, 214, 255)
                    )
                } else {
                    rounded(
                        activity,
                        Color.rgb(7, 28, 48),
                        18,
                        Color.rgb(37, 75, 100)
                    )
                }
            isClickable = true
            isFocusable = true
            setOnClickListener {
                animate()
                    .scaleX(0.985f)
                    .scaleY(0.985f)
                    .setDuration(55L)
                    .withEndAction {
                        animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(90L)
                            .start()
                        onClick()
                    }
                    .start()
            }

            addView(
                TextView(activity).apply {
                    text = title
                    textSize = 14f
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(Color.WHITE)
                }
            )

            if (!detail.isNullOrBlank()) {
                addView(
                    TextView(activity).apply {
                        text = detail
                        textSize = 11.5f
                        setTextColor(
                            if (emphasized) {
                                Color.rgb(215, 239, 250)
                            } else {
                                Color.rgb(133, 169, 192)
                            }
                        )
                        setPadding(
                            0,
                            dp(activity, 3),
                            0,
                            0
                        )
                    }
                )
            }
        }

    private fun dismissRow(
        activity: Activity,
        textValue: String,
        onClick: () -> Unit
    ): TextView =
        TextView(activity).apply {
            text = textValue
            textSize = 11f
            letterSpacing = 0.08f
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(130, 184, 214))
            setPadding(
                dp(activity, 12),
                dp(activity, 13),
                dp(activity, 12),
                dp(activity, 13)
            )
            setOnClickListener { onClick() }
        }

    private fun present(
        dialog: Dialog,
        root: View
    ) {
        dialog.setContentView(root)
        dialog.show()

        dialog.window?.let { window ->
            window.setBackgroundDrawable(
                ColorDrawable(Color.TRANSPARENT)
            )
            window.setDimAmount(0.62f)
            window.addFlags(
                WindowManager.LayoutParams.FLAG_DIM_BEHIND
            )
            window.setGravity(Gravity.BOTTOM)
            window.setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        root.alpha = 0f
        root.translationY = dp(root.context as Activity, 28).toFloat()
        root.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(220L)
            .start()
    }

    private fun rounded(
        activity: Activity,
        color: Int,
        radiusDp: Int,
        stroke: Int? = null
    ): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(activity, radiusDp).toFloat()
            if (stroke != null) {
                setStroke(dp(activity, 1), stroke)
            }
        }

    private fun gradient(
        activity: Activity,
        colors: IntArray,
        radiusDp: Int,
        stroke: Int? = null
    ): GradientDrawable =
        GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            colors
        ).apply {
            cornerRadius = dp(activity, radiusDp).toFloat()
            if (stroke != null) {
                setStroke(dp(activity, 1), stroke)
            }
        }

    private fun dp(
        activity: Activity,
        value: Int
    ): Int =
        (
            value *
                activity.resources.displayMetrics.density
            ).toInt()
}
