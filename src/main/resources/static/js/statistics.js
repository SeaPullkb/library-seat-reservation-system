document.addEventListener('DOMContentLoaded', () => {
  const data = window.libraryStatistics;
  if (!data || !window.echarts) return; // Tables remain usable if scripts cannot load.
  const options = {aria: {enabled: true}, tooltip: {trigger: 'axis', renderMode: 'richText'}, grid: {left: 55, right: 20, top: 30, bottom: 60}};
  const trend = echarts.init(document.getElementById('trend-chart'));
  trend.setOption({...options, xAxis: {type: 'category', data: data.trend.map(d => d.date)}, yAxis: {type: 'value', minInterval: 1}, series: [{name: '预约人数', type: 'line', data: data.trend.map(d => d.students), itemStyle: {color: '#245c7f'}, areaStyle: {opacity: 0.08}}]});
  const rooms = echarts.init(document.getElementById('room-chart'));
  rooms.setOption({...options, xAxis: {type: 'category', data: data.rooms.map(r => r.name), axisLabel: {width: 90, overflow: 'truncate'}}, yAxis: {type: 'value', axisLabel: {formatter: '{value}%'}}, series: [{name: '预约率（%）', type: 'bar', data: data.rooms.map(r => r.reservationRate), itemStyle: {color: '#245c7f'}, barMaxWidth: 65}]});
  const resize = () => { trend.resize(); rooms.resize(); };
  if (window.ResizeObserver) {
    const observer = new ResizeObserver(resize);
    observer.observe(document.getElementById('trend-chart'));
    observer.observe(document.getElementById('room-chart'));
  } else window.addEventListener('resize', resize);
});
