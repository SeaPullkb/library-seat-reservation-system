document.addEventListener('DOMContentLoaded', () => {
  const data = JSON.parse(document.getElementById("statistics-data").textContent);
  window.libraryStatistics = data;
  if (!data || !window.echarts) return; // Tables remain usable if scripts cannot load.
  const options = {animation: false, aria: {enabled: true}, tooltip: {trigger: 'axis', renderMode: 'richText'}, grid: {left: 48, right: 18, top: 30, bottom: 55}, textStyle: {color: '#5c6b80', fontFamily: 'system-ui, Microsoft YaHei, sans-serif'}};
  const trend = echarts.init(document.getElementById('trend-chart'));
  trend.setOption({...options, xAxis: {type: 'category', data: data.trend.map(d => d.date)}, yAxis: {type: 'value', minInterval: 1}, series: [{name: '预约人数', type: 'line', data: data.trend.map(d => d.students), symbolSize: 6, lineStyle: {width: 3}, itemStyle: {color: '#235bea'}, areaStyle: {opacity: 0.07}}]});
  const rooms = echarts.init(document.getElementById('room-chart'));
  rooms.setOption({...options, xAxis: {type: 'category', data: data.rooms.map(r => r.name), axisLabel: {width: 90, overflow: 'truncate'}}, yAxis: {type: 'value', axisLabel: {formatter: '{value}%'}}, series: [{name: '预约率（%）', type: 'bar', data: data.rooms.map(r => r.reservationRate), itemStyle: {color: '#235bea', borderRadius: [5, 5, 0, 0]}, barMaxWidth: 42}]});
  const resize = () => { trend.resize(); rooms.resize(); };
  if (window.ResizeObserver) {
    const observer = new ResizeObserver(resize);
    observer.observe(document.getElementById('trend-chart'));
    observer.observe(document.getElementById('room-chart'));
  } else window.addEventListener('resize', resize);
});
