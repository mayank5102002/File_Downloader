package com.example.filedownloader.models

import android.content.Context
import android.graphics.PorterDuff
import android.icu.text.SimpleDateFormat
import android.icu.util.Calendar
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.filedownloader.R
import java.util.*

//This adapter is used to show the list of downloads in the downloads fragment
class DownloadsAdapter(var dataList: List<DownloadModel>, private val itemClickListener: OnItemClickListener,
private val context : Context
) :
    RecyclerView.Adapter<DownloadsAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_download, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val data = dataList[position]
        holder.bind(data)
    }

    override fun getItemCount(): Int {
        return dataList.size
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView), View.OnClickListener {

        //Views of the item_download layout
        private val dayTextView: TextView = itemView.findViewById(R.id.dayTextView)
        private val iconCardView : ImageView = itemView.findViewById(R.id.iconCardView)
        private val iconImageView : ImageView = itemView.findViewById(R.id.iconImageView)
        private val titleTextView : TextView = itemView.findViewById(R.id.titleTextView)
        private val cancelImageView : ImageView = itemView.findViewById(R.id.cancelImageView)
        private val updateTextView : TextView = itemView.findViewById(R.id.updateTextView)
        private val progressTextView : TextView = itemView.findViewById(R.id.progressTextView)
        private val progressBar : ProgressBar = itemView.findViewById(R.id.progressBar)

        //Initialising the click listeners
        init {
            itemView.setOnClickListener { onViewClick(adapterPosition) }
            cancelImageView.setOnClickListener { onCancelClick(adapterPosition) }
        }

        //Binding the data to the views
        fun bind(data: DownloadModel) {
            dayTextView.text = getDate(data.time)
            updateDateView()
            setImageResources(data.status, data.fileExtension)
            titleTextView.text = editTitle(data.fileName, data.fileExtension)
            processUpdateText(data.status, data.downloadProgressBytes, data.downloadTotalBytes, data.failedMessage)
            processProgressText(data.status, data.downloadProgressPercentage)
            processProgressBar(data.status, data.downloadProgressPercentage)
        }

        //This function will check the date before it, and if any download is of the same date as
        //current then it'll make the date view invisible
        private fun updateDateView(){

            if(adapterPosition == 0){
                dayTextView.visibility = View.VISIBLE
            } else {
                val previousDate = getDate(dataList[adapterPosition - 1].time)
                val currentDate = getDate(dataList[adapterPosition].time)
                if(previousDate == currentDate){
                    dayTextView.visibility = View.GONE
                } else {
                    dayTextView.visibility = View.VISIBLE
                }
            }
        }

        //This function will process progress bar according to the status
        private fun processProgressBar(status : DownloadStatus, progress : Int){
            if(status == DownloadStatus.DOWNLOADING){
                progressBar.visibility = View.VISIBLE
                progressBar.progress = progress
                progressBar.progressTintList = ContextCompat.getColorStateList(context, R.color.green_color)
            } else if(status == DownloadStatus.COMPLETED){
                progressBar.visibility = View.GONE
            } else if(status == DownloadStatus.PAUSED){
                progressBar.visibility = View.VISIBLE
                progressBar.progress = progress
                progressBar.progressTintList = ContextCompat.getColorStateList(context, R.color.grey_color)
            } else if(status == DownloadStatus.FAILED){
                progressBar.visibility = View.VISIBLE
                progressBar.progress = 100
                progressBar.progressTintList = ContextCompat.getColorStateList(context, R.color.red_color)
            } else {
                progressBar.visibility = View.VISIBLE
                progressBar.progress = progress
                progressBar.progressTintList = ContextCompat.getColorStateList(context, R.color.yellow_color)
            }
        }

        //This function will process progress text according to the status
        private fun processProgressText(status : DownloadStatus, progress : Int){
            if(status == DownloadStatus.DOWNLOADING){
                progressTextView.visibility = View.VISIBLE
                val progressText = "$progress%"
                progressTextView.text = progressText
            } else {
                progressTextView.visibility = View.GONE
            }
        }

        //This function will update the text view according to the status
        private fun processUpdateText(status : DownloadStatus, downloadBytes : String, totalBytes : String, failedMessage : String){
            if(status == DownloadStatus.DOWNLOADING){
                updateTextView.visibility = View.VISIBLE
                val updateText = "$downloadBytes/$totalBytes"
                updateTextView.text = updateText
            } else if(status == DownloadStatus.COMPLETED){
                updateTextView.visibility = View.GONE
            } else {
                updateTextView.text = failedMessage
            }
        }

        //This function will change the icon and background color according to the status
        private fun setImageResources(status : DownloadStatus, fileExtension: String) {
            if(status == DownloadStatus.DOWNLOADING){
                changeBgColor(R.color.green_ellipse_color)
                iconImageView.setImageResource(R.drawable.pause_green_icon)
            } else if(status == DownloadStatus.PAUSED){
                changeBgColor(R.color.grey_ellipse_color)
                iconImageView.setImageResource(R.drawable.resume_grey_icon)
            } else if(status == DownloadStatus.COMPLETED){
                changeBgColor(R.color.blue_ellipse_color)
                if(fileExtension == ".mp3"){
                    iconImageView.setImageResource(R.drawable.mp3_icon)
                } else if(fileExtension == ".mp4"){
                    iconImageView.setImageResource(R.drawable.mp4_icon)
                } else if(fileExtension == "jpq" || fileExtension == "jpeg" || fileExtension == "png"){
                    iconImageView.setImageResource(R.drawable.img_icon)
                } else {
                    iconImageView.setImageResource(R.drawable.file_icon)
                }
            } else if(status == DownloadStatus.FAILED){
                changeBgColor(R.color.red_ellipse_color)
                iconImageView.setImageResource(R.drawable.error_red_icon)
            } else {
                changeBgColor(R.color.yellow_ellipse_color)
                iconImageView.setImageResource(R.drawable.paused_yellow_icon)
            }
        }


        private fun changeBgColor(resId : Int){
            iconCardView.setColorFilter(ContextCompat.getColor(context, resId), PorterDuff.Mode.SRC_IN)
        }

        //This function returns the date in the format of "Today", "Yesterday" or "d MMM yyyy"
        private fun getDate(date: Long): String {
            val todayCalendar = Calendar.getInstance()
            val dateToCheck = Calendar.getInstance().apply {
                timeInMillis = date
            }

            val todayYear = todayCalendar.get(Calendar.YEAR)
            val todayMonth = todayCalendar.get(Calendar.MONTH)
            val todayDay = todayCalendar.get(Calendar.DAY_OF_MONTH)

            val yearToCheck = dateToCheck.get(Calendar.YEAR)
            val monthToCheck = dateToCheck.get(Calendar.MONTH)
            val dayToCheck = dateToCheck.get(Calendar.DAY_OF_MONTH)

            return when {
                todayYear == yearToCheck && todayMonth == monthToCheck && todayDay == dayToCheck -> "Today"
                todayYear == yearToCheck && todayMonth == monthToCheck && todayDay - 1 == dayToCheck -> "Yesterday"
                else -> SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(date))
            }
        }

        private fun getMonth(month : Int) : String {
            return when (month) {
                0 -> "Jan"
                1 -> "Feb"
                2 -> "Mar"
                3 -> "Apr"
                4 -> "May"
                5 -> "Jun"
                6 -> "Jul"
                7 -> "Aug"
                8 -> "Sep"
                9 -> "Oct"
                10 -> "Nov"
                11 -> "Dec"
                else -> "Jan"
            }
        }

        //This function edits the title of the file
        private fun editTitle(fileName : String, fileExtension : String) : String {
            var newFileName = fileName
            if(fileName.length > 20) {
                newFileName = fileName.substring(0, 20) + "..." + fileName.substring(fileName.length - 5, fileName.length)
            }
            return "$newFileName.$fileExtension"
        }

        //Click listeners
        private fun onViewClick(position: Int) {
            itemClickListener.onViewClick(position)
        }

        private fun onCancelClick(position: Int) {
            itemClickListener.onCancelButtonClick(position)
        }

        override fun onClick(v: View?) {
            TODO("Not yet implemented")
        }
    }

    interface OnItemClickListener {
        fun onCancelButtonClick(position: Int)
        fun onViewClick(position: Int)
    }
}