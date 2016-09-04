package com.asiainfo.ocdp.local.shaanxiyidong.label

import com.asiainfo.ocdp.stream.common.{LabelProps, StreamingCache}
import com.asiainfo.ocdp.stream.constant.LabelConstant
import com.asiainfo.ocdp.stream.label.Label
import com.asiainfo.ocdp.stream.tools.DateFormatUtils
import org.slf4j.LoggerFactory

import scala.collection.mutable

/**
  * Created by gengwang on 16/8/11.
  */
class AccumulateLabel extends Label {
  //labelMap (isAccu_security,true)(isAccu_tour,true)
  val logger = LoggerFactory.getLogger(this.getClass)
  val isSecurityAccuKey = "isAccu_security"
  val isTourAccuKey = "isAccu_tour"

  lazy val thresholdValue = conf.getLong(LabelConstant.STAY_TIMEOUT, LabelConstant.DEFAULT_TIMEOUT_VALUE)

  override def attachLabel(line: Map[String, String], cache: StreamingCache, labelQryData: mutable.Map[String, mutable.Map[String, String]]): (Map[String, String], StreamingCache) = {
    //2更新标签
    //2.1 根据标签缓存和当前日志信息更新标签
    //a. 获取locationStayRule的cache

   // val cacheInstance = if (cache == null) new LabelProps else cache.asInstanceOf[LabelProps]

    val cacheInstance = if (cache == null) new LabelProps else cache.asInstanceOf[LabelProps]
    val interval = conf.get("interval").toLong
    // cache中各区域的属性map
    val cacheImmutableMap = cacheInstance.labelsPropList
    // map属性转换
    val cacheMutableMap = transformCacheMap2mutableMap(cacheImmutableMap)
    // mcsource 打标签用[初始化标签值]
    val labelMap = fieldsMap()
    println("timestamp" + line("timestamp"))
    val currentTime = line("timestamp").toLong
    println("currentTime" + currentTime)

    val tour_area  = line("tour_area")
    val security_area = line("security_area")
    val normal_imsi = line("imsi")
    val tour_cache_key = "@tour@" + tour_area
    val security_cache_key = "@security@" + security_area
    labelMap.update(isSecurityAccuKey, "false")
    labelMap.update(isTourAccuKey, "false")

    //分别根据 imsi@旅游区域 和 imsi@安防区域 查询缓存
    cacheMutableMap.get(tour_cache_key)
    match{
      case None => {
        labelMap.update(isTourAccuKey, "falsenonetour")
        val cacheAccuLabelsMap = mutable.Map[String, String]()
        cacheAccuLabelsMap += ("timestamp" -> currentTime.toString)
        cacheMutableMap += (tour_cache_key -> cacheAccuLabelsMap)
        //若缓存中不存在该 imsi@旅游区域,则将isTourAccuKey设为true,指需要将其加入累计人数的计数中
        labelMap.update(isTourAccuKey, "truenonetour" + "tourchachekey:" + tour_cache_key)
      }
      case Some(cacheAccuLabelsMap) => {

        val cacheTime = cacheAccuLabelsMap.get("timestamp").get.toLong
        labelMap.update(isTourAccuKey, "falsesometour"+"cachetime" + cacheTime + "currenttime" + currentTime + "tourchachekey:" + tour_cache_key)
        if (currentTime >= ((interval * 1000) + cacheTime)) {
          //Update the latest time to the site cache
          cacheAccuLabelsMap += ("timestamp" -> currentTime.toString)
          cacheMutableMap += (tour_cache_key -> cacheAccuLabelsMap)
          labelMap.update(isTourAccuKey, "true")
        }
      }
    }
    cacheMutableMap.get(security_cache_key)
    match{
      case None => {
        val cacheAccuLabelsMap = mutable.Map[String, String]()
        cacheAccuLabelsMap += ("timestamp" -> currentTime.toString)
        cacheMutableMap += (security_cache_key -> cacheAccuLabelsMap)
        //若缓存中不存在该 imsi@安防区域,则将isSecurityAccuKey设为true,指需要将其加入累计人数的计数中
        labelMap.update(isSecurityAccuKey, "truenonesec")
      }
      case Some(cacheAccuLabelsMap) => {
        val cacheTime = cacheAccuLabelsMap.get("timestamp").get.toLong

        if (currentTime >= ((interval * 1000) + cacheTime)) {
          //
          cacheAccuLabelsMap += ("timestamp" -> currentTime.toString)
          cacheMutableMap += (security_cache_key -> cacheAccuLabelsMap)
          labelMap.update(isSecurityAccuKey, "truesomesec")
        }
      }
    }
    labelMap ++= line

    //3更新缓存
    // map属性转换
    cacheInstance.labelsPropList = transformCacheMap2ImmutableMap(cacheMutableMap)
    (labelMap.toMap, cacheInstance)

  }

  /**
    * 把cache的数据转为可变map
    */
  private def transformCacheMap2mutableMap(cacheInfo: Map[String, Map[String, String]]) = {
    val labelsPropMap = mutable.Map[String, mutable.Map[String, String]]()
    cacheInfo.map(infoMap => {
      val copProp = mutable.Map[String, String]()
      infoMap._2.foreach(copProp += _)
      labelsPropMap += (infoMap._1 -> copProp)
    })
    labelsPropMap
  }

  /**
    * 编辑完chache中的内容后重新置为不可变类属
    */
  private def transformCacheMap2ImmutableMap(labelsPropMap: mutable.Map[String, mutable.Map[String, String]]) = {
    if (labelsPropMap.isEmpty) Map[String, Map[String, String]]() else labelsPropMap.map(propSet => (propSet._1, propSet._2.toMap)).toMap
  }

}

/*
class LabelProps extends StreamingCache with Serializable {
  var labelsPropList = Map[String, Map[String, String]]()
}
*/