<script setup lang="ts">
import type { BookingStatus } from '@/types/booking';
import { bookingStatusMeta } from '@/types/booking';

defineProps<{
  status: BookingStatus;
}>();
</script>

<template>
  <el-tag :type="bookingStatusMeta(status).type" effect="light">
    {{ bookingStatusMeta(status).label }}
  </el-tag>
</template>
