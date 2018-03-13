package com.log28


import android.os.Bundle
import android.support.v4.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.support.v7.widget.DividerItemDecoration
import android.support.v7.widget.LinearLayoutManager
import android.util.AttributeSet
import android.util.Log
import com.xwray.groupie.GroupAdapter
import com.xwray.groupie.kotlinandroidextensions.ViewHolder
import io.realm.RealmResults
import kotlinx.android.synthetic.main.fragment_cycle_overview.*
import com.log28.groupie.OverviewItem
import devs.mulham.horizontalcalendar.utils.Utils
import java.util.*


/**
 * A simple [Fragment] subclass.
 * Use the [CycleOverview.newInstance] factory method to
 * create an instance of this fragment.
 */
class CycleOverview : Fragment() {
    private val periodDates = getPeriodDaysDecending()
    private val dayData = getDataByDate(Calendar.getInstance())
    private val cycleInfo = getCycleInfo()
    private val groupAdapter = GroupAdapter<ViewHolder>()


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_cycle_overview, container, false)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        cycleInfo.removeAllChangeListeners()
        periodDates.removeAllChangeListeners()
        dayData.removeAllChangeListeners()
    }

    // this should fix the case where the user returns to the activity on a subsequent date
    override fun onResume() {
        Log.d("OVERVIEW", "resuming")
        super.onResume()
        calculateNextPeriod(findCycleStart(periodDates))
        setupLoggedToday()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d("OVERVIEW", "view created")
        periodDates.addChangeListener {
            results, changeset -> Log.d("OVERVIEW", "period dates changed")
            calculateNextPeriod(findCycleStart(results))
        }

        cycleInfo.addChangeListener<CycleInfo> {
            _, _ ->
            calculateNextPeriod(findCycleStart(periodDates))
        }

        dayData.addChangeListener<DayData> {
            _, changeSet ->
            if (changeSet != null) setupLoggedToday()
        }

        val layout = LinearLayoutManager(context)
        val dividerItem = DividerItemDecoration(context, layout.orientation)

        today_log_list.apply {
            layoutManager = layout
            adapter = groupAdapter
            this.addItemDecoration(dividerItem)
        }
    }

    private fun setupLoggedToday() {
        if (!dayData.isValid)
            return

        groupAdapter.clear()
        //setup recycler view
        if (dayData.symptoms.isEmpty() && dayData.notes.isBlank())
            logged_today.setText(R.string.nothing_logged)
        else {
            logged_today.setText(R.string.logged_today)
            dayData.symptoms.forEach {
                groupAdapter.add(OverviewItem(it.name))
            }
            if (dayData.notes.isNotBlank())
                groupAdapter.add(OverviewItem(context?.resources!!.getString(R.string.notes_prefix, dayData.notes)))
        }
    }

    private fun findCycleStart(periodDates: RealmResults<DayData>): Long {
        periodDates.forEachIndexed { index, dayData ->
            //if this is the first day entered, or the previous period date is not the day before the current one, return
            if (index == periodDates.lastIndex || dayData.date - 1 != periodDates[index+1]?.date) {
                Log.d("OVERVIEW", "found cycle start at ${dayData.date}")
                return dayData.date
            }
        }
        // return 0 on failure. This will happen if the DB is empty
        return 0
    }

    private fun calculateNextPeriod(cycleStartDate: Long) {
        Log.d("OVERVIEW", "calculate next period called")

        if (cycleStartDate == 0L) return
        val cycleStart = cycleStartDate.toCalendar()

        Log.d("OVERVIEW", "calculating next period")

        // updateModel the text views with the correct days until
        val cycleDay = Utils.daysBetween(cycleStart, Calendar.getInstance())
        Log.d("OVERVIEW",
                "cycle length ${cycleInfo.cycleLength}, periodLength ${cycleInfo.periodLength} cycle day is $cycleDay")
        // on period
        if (cycleDay < cycleInfo.periodLength) {
            days_until_text.text = getString(R.string.days_left_in_period)
            days_until_number.text = (cycleInfo.periodLength - cycleDay).toString()
        } else if (cycleDay < cycleInfo.cycleLength) {
            days_until_text.text = getString(R.string.days_until_period)
            days_until_number.text = (cycleInfo.cycleLength - cycleDay).toString()
        } else {
            days_until_text.text = getString(R.string.days_late)
            days_until_number.text = (cycleDay - cycleInfo.cycleLength).toString()
        }

        // updateModel the progress bar
        cycle_view.setCycleData(cycleInfo.cycleLength, cycleInfo.periodLength, cycleDay)
        this.view?.invalidate()
    }

    companion object {

        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @return A new instance of fragment CycleOverview.
         */
        fun newInstance(): CycleOverview {
            val fragment = CycleOverview()
            val args = Bundle()
            fragment.arguments = args
            return fragment
        }
    }

}

// this class draws the custom graphic for showing the cycle
// it is not designed to be used outside of this fragment
class CycleView(context: Context, attrs: AttributeSet) : View(context, attrs) {
    private val paintRed = Paint(Paint.ANTI_ALIAS_FLAG)
    private val paintGrey = Paint(Paint.ANTI_ALIAS_FLAG)
    private val paintIndicator = Paint(Paint.ANTI_ALIAS_FLAG)

    private var periodLength = 5
    private var cycleLength = 28
    private var cycleDay: Int? = null

    init {
        paintRed.color = Color.parseColor("#D32F2F")
        paintRed.style = Paint.Style.FILL
        paintGrey.color = Color.parseColor("#BDBDBD")
        paintGrey.style = Paint.Style.FILL
        paintIndicator.color = Color.parseColor("#FFFFFF")
        paintIndicator.alpha = 80
        paintIndicator.style = Paint.Style.FILL
    }

    fun setCycleData(cycleLength: Int, periodLength: Int, cycleDay: Int) {
        this.periodLength = periodLength
        this.cycleLength = cycleLength
        this.cycleDay = cycleDay
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        setMeasuredDimension(measureWidth(widthMeasureSpec), measureHeight(heightMeasureSpec))
    }

    private fun measureWidth(widthMeasureSpec: Int): Int {
        val mode = MeasureSpec.getMode(widthMeasureSpec)
        val size = MeasureSpec.getSize(widthMeasureSpec)

        if (mode == MeasureSpec.EXACTLY)
            return size
        if (mode == MeasureSpec.AT_MOST)
            return size
        else
            return dpOrSpToPx(context, 82.toFloat()).toInt()
    }

    private fun measureHeight(heightMeasureSpec: Int): Int {
        val mode = MeasureSpec.getMode(heightMeasureSpec)
        val size = MeasureSpec.getSize(heightMeasureSpec)

        if (mode == MeasureSpec.EXACTLY)
            return size
        else
            return dpOrSpToPx(context, 32.toFloat()).toInt()
    }


    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)
        canvas?.drawRect(0.toFloat(),0.toFloat(),width.toFloat(),height.toFloat(), paintGrey)
        canvas?.drawRect(0.toFloat(),0.toFloat(),width * periodLength / cycleLength.toFloat(),height.toFloat(), paintRed)

        // only draw the cycle indicator if the period is not late
        if (cycleDay != null && cycleDay!! < cycleLength) {
            canvas?.drawRect(width.toFloat() * (cycleDay!! + 1) / cycleLength,0.toFloat(),width.toFloat(),
                    height.toFloat(), paintIndicator)
        }
    }

    // convert a dp or sp value to px
    private fun dpOrSpToPx(context: Context, dpOrSpValue: Float): Float {
        return dpOrSpValue * context.resources.displayMetrics.density
    }
}