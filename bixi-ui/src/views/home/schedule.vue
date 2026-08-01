<template>
  <el-card class="schedule-card">
    <el-calendar v-model="value" />
  </el-card>
</template>

<script setup lang="ts" name="systemSysSchedule">
const value = ref(new Date())
</script>
<style>
.el-calendar-table .el-calendar-day{
  height: 44px;
}

.schedule-card {
  height: 100%;
}
</style>
