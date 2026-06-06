package com.example.adfalls.ui.metrics

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.adfalls.R
import com.example.adfalls.data.model.AdMetric
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToInt

class AdMetricsAdapter : ListAdapter<AdMetric, AdMetricsAdapter.MetricViewHolder>(Diff) {
    private val numberFormat = NumberFormat.getIntegerInstance(Locale.CHINA)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MetricViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_ad_metric, parent, false)
        return MetricViewHolder(view)
    }

    override fun onBindViewHolder(holder: MetricViewHolder, position: Int) {
        val maxExposures = currentList.maxOfOrNull { it.exposures }?.coerceAtLeast(1) ?: 1
        holder.bind(getItem(position), position + 1, maxExposures)
    }

    inner class MetricViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val rank: TextView = itemView.findViewById(R.id.metric_rank)
        private val title: TextView = itemView.findViewById(R.id.metric_title)
        private val meta: TextView = itemView.findViewById(R.id.metric_meta)
        private val ctr: TextView = itemView.findViewById(R.id.metric_ctr)
        private val bar: View = itemView.findViewById(R.id.metric_exposure_bar)
        private val exposures: TextView = itemView.findViewById(R.id.metric_exposures)
        private val clicks: TextView = itemView.findViewById(R.id.metric_clicks)
        private val interactions: TextView = itemView.findViewById(R.id.metric_interactions)

        fun bind(metric: AdMetric, position: Int, maxExposures: Int) {
            rank.text = position.toString().padStart(2, '0')
            title.text = metric.title
            meta.text = listOf(
                metric.channel,
                metric.type,
                metric.tags.take(2).joinToString(" ") { "#$it" }
            ).filter { it.isNotBlank() }.joinToString(" · ")
            ctr.text = metric.ctr.toPercent()
            exposures.text = "曝光 ${numberFormat.format(metric.exposures)}"
            clicks.text = "点击 ${numberFormat.format(metric.clicks)}"
            interactions.text = "互动 ${numberFormat.format(metric.interactionCount)}"
            itemView.contentDescription = "${metric.title}，曝光 ${metric.exposures}，点击 ${metric.clicks}，CTR ${metric.ctr.toPercent()}"
            updateBar(metric.exposures, maxExposures)
        }

        private fun updateBar(value: Int, max: Int) {
            val ratio = value.toFloat() / max.toFloat()
            bar.post {
                val parentWidth = (bar.parent as? View)?.width ?: return@post
                val targetWidth = (parentWidth * ratio).roundToInt().coerceAtLeast(1)
                if (bar.layoutParams.width != targetWidth) {
                    bar.layoutParams = bar.layoutParams.apply { width = targetWidth }
                }
            }
        }
    }

    private object Diff : DiffUtil.ItemCallback<AdMetric>() {
        override fun areItemsTheSame(oldItem: AdMetric, newItem: AdMetric): Boolean = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: AdMetric, newItem: AdMetric): Boolean = oldItem == newItem
    }
}

internal fun Double.toPercent(): String {
    return String.format(Locale.CHINA, "%.2f%%", this * 100.0)
}
