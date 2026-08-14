<script setup lang="ts">
import { computed } from 'vue';
import type { TripSeat } from '@/types/trip';
import { isSelectableSeat, seatStatusLabel } from '@/types/trip';

const props = defineProps<{
  seats: TripSeat[];
  modelValue: string | null;
}>();

const emit = defineEmits<{
  'update:modelValue': [value: string | null];
}>();

const sortedSeats = computed(() =>
  [...props.seats].sort((left, right) =>
    left.seatNumber.localeCompare(right.seatNumber),
  ),
);

function seatClass(seat: TripSeat): string {
  return `seat seat-${seat.status.toLowerCase()}${
    props.modelValue === seat.seatNumber ? ' seat-selected' : ''
  }`;
}

function handleSelect(seat: TripSeat) {
  if (!isSelectableSeat(seat)) {
    return;
  }
  emit('update:modelValue', seat.seatNumber);
}
</script>

<template>
  <div class="seat-grid">
    <button
      v-for="seat in sortedSeats"
      :key="seat.seatNumber"
      type="button"
      :class="seatClass(seat)"
      :disabled="!isSelectableSeat(seat)"
      @click="handleSelect(seat)"
    >
      <span class="seat-number">{{ seat.seatNumber }}</span>
      <span class="seat-status">{{ seatStatusLabel(seat.status) }}</span>
    </button>
  </div>
</template>

<style scoped>
.seat-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(88px, 1fr));
  gap: 12px;
}

.seat {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 72px;
  border-radius: 8px;
  border: 1px solid #dcdfe6;
  background: #fff;
  cursor: pointer;
  transition: all 0.2s ease;
}

.seat:disabled {
  cursor: not-allowed;
  opacity: 0.85;
}

.seat-available:hover:not(:disabled) {
  border-color: #409eff;
}

.seat-selected {
  border-color: #409eff;
  box-shadow: 0 0 0 2px rgba(64, 158, 255, 0.15);
  background: #ecf5ff;
}

.seat-locked {
  background: #fdf6ec;
  border-color: #e6a23c;
}

.seat-sold {
  background: #fef0f0;
  border-color: #f56c6c;
}

.seat-number {
  font-weight: 600;
  color: #303133;
}

.seat-status {
  margin-top: 4px;
  font-size: 12px;
  color: #909399;
}
</style>
