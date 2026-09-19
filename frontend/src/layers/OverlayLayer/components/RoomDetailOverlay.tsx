import React from 'react';
import {motion, useReducedMotion} from 'framer-motion';
import {ArrowLeft, User, Users, Layers, Calendar, ImageIcon} from 'lucide-react';
import {useNavigate, useParams} from '@tanstack/react-router';
import toast from 'react-hot-toast';
import {useTranslation} from 'react-i18next';
import EmptyState from '../../../components/polish/EmptyState';
import ScheduleTimelineDesktop from '../../../features/schedule/components/ScheduleTimelineDesktop';
import ScheduleListMobile from '../../../features/schedule/components/ScheduleListMobile';
import type {DirectSelectionRange} from '../../../features/schedule/hooks/useDirectBookingSelection';
import {formatLocalDateTime} from '../../../lib/datetime/formatLocalDateTime';
import {parseDateKey, normalizeDate, formatDateForApi} from '../../../lib/datetime/dateKey';
import {useHubStore} from '../../../store/useHubStore';
import {useScheduleQuery} from '../../../services/hooks/useScheduleQuery';
import {useCreateBookingMutation} from '../../../services/hooks/useCreateBookingMutation';
import {useMediaQuery} from '../../../hooks/useMediaQuery';
import MagneticButton from '../../../components/motion/MagneticButton';
import {getApiErrorMessage, getApiStatus} from '../../../lib/httpError';
import {motionTokens} from '../../../lib/motionTokens';
import {useRoomQuery} from '../../../services/hooks/useRoomQuery';
import {useHolidaysQuery} from '../../../services/hooks/useHolidaysQuery';
import SeoMeta from '../../../components/seo/SeoMeta';
import {absoluteUrl} from '../../../lib/seo';
import NotFoundPage from '../../../pages/NotFoundPage';

const RoomDetailOverlay = () => {
  const {t, i18n} = useTranslation();
  const navigate = useNavigate();
  const params = useParams({strict: false});
  const isDesktop = useMediaQuery('(min-width: 1024px)');
  const selectedDateKey = useHubStore((state) => state.selectedDateKey) || formatDateForApi(normalizeDate(new Date()));
  const activeRoomId = useHubStore((state) => state.activeRoomId);
  const cameraPose = useHubStore((state) => state.cameraPose);
  const reducedMotion = useReducedMotion();
  const locale = i18n.language === 'ru' ? 'ru-RU' : 'en-US';
  const selectedDate = parseDateKey(selectedDateKey);

  const currentRoomId = activeRoomId ?? params.roomId ?? null;
  const lastRoomIdRef = React.useRef(currentRoomId);
  if (currentRoomId) {
    lastRoomIdRef.current = currentRoomId;
  }
  const roomId = currentRoomId ?? lastRoomIdRef.current;

  const schedule = useScheduleQuery(selectedDate);
  const roomQuery = useRoomQuery(roomId);
  const holidaysQuery = useHolidaysQuery(selectedDate.getFullYear(), 'RU');
  const [selectedRange, setSelectedRange] = React.useState<DirectSelectionRange | null>(null);
  const [nowTs, setNowTs] = React.useState(Date.now());

  const roomModel = React.useMemo(() => {
    if (!roomId) return {slots: []};

    return {
      slots: schedule.model.slots
        .map((slot) => ({...slot, rooms: slot.rooms.filter((room) => room.roomId === roomId)}))
        .filter((slot) => slot.rooms.length > 0),
    };
  }, [roomId, schedule.model.slots]);

  const roomMeta = React.useMemo(() => {
    const firstRoom = roomModel.slots[0]?.rooms[0];
    if (firstRoom) {
      return {
        roomName: firstRoom.roomName,
        floor: firstRoom.floor,
        capacity: firstRoom.capacity,
        coverImageUrl: firstRoom.coverImageUrl ?? null,
      };
    }
    if (!roomQuery.data) return null;
    return {
      roomName: roomQuery.data.name,
      floor: roomQuery.data.floor,
      capacity: roomQuery.data.capacity,
      coverImageUrl: null,
    };
  }, [roomModel.slots, roomQuery.data]);
  const [imageError, setImageError] = React.useState(false);
  const holidayDates = React.useMemo(() => new Set((holidaysQuery.data ?? []).map((item) => item.date)), [holidaysQuery.data]);
  const isHolidaySelected = holidayDates.has(formatDateForApi(selectedDate));

  const updatedSecondsAgo = Math.max(0, Math.floor((nowTs - schedule.dataUpdatedAt) / 1000));

  React.useEffect(() => {
    if (currentRoomId && activeRoomId !== currentRoomId) {
      useHubStore.getState().enterRoom(currentRoomId);
    }
  }, [activeRoomId, currentRoomId]);

  React.useEffect(() => {
    const id = window.setInterval(() => setNowTs(Date.now()), 1000);
    return () => window.clearInterval(id);
  }, []);

  React.useEffect(() => {
    setImageError(false);
  }, [roomId, selectedDateKey, roomMeta?.coverImageUrl]);

  const createMutation = useCreateBookingMutation(selectedDateKey, roomId ?? '');

  const handleBook = async () => {
    if (!selectedRange || !roomId || holidaysQuery.isPending || holidaysQuery.isError) return;
    if (isHolidaySelected) {
      toast.error(t('booking.disable.holiday'));
      return;
    }
    try {
      await createMutation.mutateAsync({
        roomId,
        startTime: formatLocalDateTime(selectedDate, selectedRange.start),
        endTime: formatLocalDateTime(selectedDate, selectedRange.end),
      });
      toast.success(t('roomDetail.bookingCreated'));
      setSelectedRange(null);
    } catch (error: unknown) {
      toast.error(getApiErrorMessage(error, t('roomDetail.bookingError')));
    }
  };

  if (!roomId) return null;
  if (roomQuery.isError && [400, 404].includes(getApiStatus(roomQuery.error) ?? 0)) {
    return <NotFoundPage />;
  }

  const closeRoomDetail = () => {
    navigate({to: '/schedule'});
  };

  const roomTitle = roomMeta?.roomName ?? t('roomDetail.fallbackRoomName');
  const roomDescription = `${roomTitle}, ${t('roomDetail.floor', {floor: roomMeta?.floor ?? 0})}, ${t('roomDetail.capacity', {count: roomMeta?.capacity ?? 0})}`;
  const canonicalUrl = absoluteUrl(`/schedule/room/${roomId}`);
  const roomJsonLd = roomMeta
    ? ({
        '@context': 'https://schema.org',
        '@type': 'Place',
        name: roomMeta.roomName,
        image: roomMeta.coverImageUrl ? [roomMeta.coverImageUrl] : undefined,
        maximumAttendeeCapacity: roomMeta.capacity,
        floorLevel: String(roomMeta.floor),
      } as Record<string, unknown>)
    : null;

  return (
    <>
    <SeoMeta title={`${roomTitle} | RoomFlow`} description={roomDescription} url={canonicalUrl} jsonLd={roomJsonLd} />
    <motion.div
      key="room-detail-overlay"
      data-cursor-scope="book"
      className="pointer-events-auto absolute inset-0 z-overlay flex items-start justify-center overflow-y-auto p-2 pb-28 pt-6 sm:p-4 sm:pb-32 sm:pt-8"
      initial={reducedMotion ? false : {opacity: 0}}
      animate={reducedMotion ? undefined : {opacity: 1, scale: cameraPose === 'room' ? 1 : 0.98}}
      exit={reducedMotion ? undefined : {opacity: 0}}
      transition={motionTokens.overlay}
      onClick={closeRoomDetail}
    >

      <motion.div
        layoutId={`room-card-${roomId}`}
        layout="position"
        transition={{type: 'spring', stiffness: 280, damping: 24}}
        style={{borderRadius: 40}}
        className="rf-modal relative flex max-h-[calc(100dvh-8.5rem)] w-full max-w-5xl flex-col overflow-hidden rounded-[2.5rem] border border-white/10 bg-card/40 p-2 shadow-2xl backdrop-blur-3xl"
        onClick={(event) => event.stopPropagation()}
      >
        <div className="flex h-full flex-col overflow-y-auto overflow-x-hidden rounded-[2rem] bg-background/20 rf-scrollbar">
          <motion.div
            initial={reducedMotion ? false : 'hidden'}
            animate={reducedMotion ? undefined : 'visible'}
            variants={{visible: {transition: {staggerChildren: 0.08}}, hidden: {}}}
            className="flex flex-col gap-3 p-2 sm:p-3"
          >
            {/* Header / Image Block */}
            <motion.div
              variants={{hidden: {opacity: 0, y: 10}, visible: {opacity: 1, y: 0}}}
              className="relative aspect-[16/9] w-full shrink-0 overflow-hidden rounded-[2rem] bg-white/5 md:aspect-[24/7]"
            >
              {roomMeta?.coverImageUrl && !imageError ? (
                <img
                  src={roomMeta.coverImageUrl}
                  alt={`${roomMeta.roomName} ${t('room.imageAlt')}`}
                  loading="lazy"
                  decoding="async"
                  onError={() => setImageError(true)}
                  className="h-full w-full object-cover"
                />
              ) : (
                <div className="flex h-full w-full flex-col items-center justify-center text-muted-foreground opacity-40">
                  <ImageIcon size={48} className="mb-3" />
                  <span className="text-sm">{t('room.imageUnavailable')}</span>
                </div>
              )}
              
              {/* Gradient overlay for readability */}
              <div className="pointer-events-none absolute inset-0 bg-gradient-to-t from-background/95 via-background/40 to-transparent" />
              <div className="pointer-events-none absolute inset-0 bg-gradient-to-b from-background/50 via-transparent to-transparent" />

              {/* Floating Back Button */}
              <div className="absolute left-4 top-4 z-10 sm:left-6 sm:top-6">
                <MagneticButton
                  onClick={closeRoomDetail}
                  data-cursor="view"
                  data-cursor-text={t('cursor.back')}
                  className="inline-flex h-10 w-10 items-center justify-center rounded-full border border-white/20 bg-background/50 text-foreground shadow-lg backdrop-blur-md transition-colors hover:bg-white/20 sm:h-auto sm:w-auto sm:px-4 sm:py-2"
                >
                  <ArrowLeft size={16} className="sm:mr-2" /> 
                  <span className="hidden text-sm font-semibold sm:inline">{t('common.back')}</span>
                </MagneticButton>
              </div>

              {/* Floating Room Info */}
              <div className="absolute bottom-4 left-4 right-4 z-10 sm:bottom-6 sm:left-6 sm:right-6">
                <motion.h2 
                  layoutId={`room-title-${roomId}`} 
                  transition={motionTokens.card} 
                  className="m-0 text-3xl font-bold text-white shadow-black drop-shadow-md sm:text-4xl"
                >
                  {roomMeta?.roomName ?? t('roomDetail.fallbackRoomName')}
                </motion.h2>
                
                {roomMeta && (
                  <div className="mt-3 flex flex-wrap items-center gap-2">
                    <span className="flex items-center gap-1.5 rounded-full border border-white/20 bg-background/60 px-3 py-1.5 text-xs font-medium text-foreground backdrop-blur-md">
                      <Users size={14} className="text-primary/80" /> {t('roomDetail.capacity', {count: roomMeta.capacity})}
                    </span>
                    <span className="flex items-center gap-1.5 rounded-full border border-white/20 bg-background/60 px-3 py-1.5 text-xs font-medium text-foreground backdrop-blur-md">
                      <Layers size={14} className="text-primary/80" /> {t('roomDetail.floor', {floor: roomMeta.floor})}
                    </span>
                    <span className="flex items-center gap-1.5 rounded-full border border-white/20 bg-background/60 px-3 py-1.5 text-xs font-medium text-foreground backdrop-blur-md">
                      <Calendar size={14} className="text-primary/80" /> 
                      {selectedDate.toLocaleDateString(locale, {day: 'numeric', month: 'long'})}
                    </span>
                  </div>
                )}
              </div>
            </motion.div>

            {/* Content Body */}
              {holidaysQuery.isPending && <p role="status">{t('calendar.loading')}</p>}
              {holidaysQuery.isError && (
                <div role="alert" className="rounded-2xl border border-amber-400/30 bg-amber-400/10 p-4 text-sm">
                  <p>{t('calendar.unavailable')}</p>
                  <button type="button" className="mt-2 underline" onClick={() => void holidaysQuery.refetch()}>{t('calendar.retry')}</button>
                </div>
              )}
              {holidaysQuery.isSuccess && holidaysQuery.data.length === 0 && <p className="text-sm text-muted-foreground">{t('calendar.empty')}</p>}

            {isHolidaySelected && (
              <div className="rounded-[2rem] border border-white/20 bg-white/10 px-6 py-4 text-sm text-foreground backdrop-blur-md">
                {t('roomDetail.holidayBanner')}
              </div>
            )}
            {schedule.isLoading ? (
              <div className="rounded-[2rem] border border-white/10 bg-white/5 px-6 py-8 text-center text-sm text-muted-foreground backdrop-blur-md">
                {t('roomDetail.loadingSchedule')}
              </div>
            ) : schedule.isError ? (
              <div className="rounded-[2rem] border border-danger/30 bg-danger/10 px-6 py-8 text-center text-sm text-danger backdrop-blur-md">
                {t('roomDetail.errorSchedule')}
                <br />
                <button
                  type="button"
                  className="mt-3 underline"
                  data-cursor="book"
                  data-cursor-text={t('cursor.retry')}
                  onClick={() => void schedule.refetch()}
                >
                  {t('common.retry')}
                </button>
              </div>
            ) : roomModel.slots.length === 0 ? (
              <EmptyState title={t('roomDetail.emptyTitle')} description={t('roomDetail.emptyDescription')} icon={User} />
            ) : (
              <motion.div
                initial={reducedMotion ? false : 'hidden'}
                animate={reducedMotion ? undefined : 'visible'}
                variants={{visible: {transition: {staggerChildren: 0.08}}, hidden: {}}}
                className="grid gap-3"
              >
                {/* Timeline Box */}
                <motion.div variants={{hidden: {opacity: 0, y: 10}, visible: {opacity: 1, y: 0}}} className="rounded-[2rem] border border-white/10 bg-white/5 p-4 sm:p-6 backdrop-blur-md">
                  <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
                    <span className="rounded-full border border-white/15 bg-background/50 px-3 py-1.5 text-xs text-muted-foreground">
                      {t('roomDetail.updatedAgo', {seconds: updatedSecondsAgo})}
                    </span>
                    <span className="rounded-full border border-primary/30 bg-primary/10 px-3 py-1.5 text-xs text-primary">
                      {isDesktop ? t('roomDetail.desktopHint') : t('roomDetail.mobileHint')}
                    </span>
                  </div>
                  <div className="min-h-0 max-h-[35dvh] overflow-y-auto overflow-x-hidden pr-1 rf-scrollbar sm:max-h-[45dvh] lg:max-h-[50dvh]">
                    {isDesktop ? (
                      <ScheduleTimelineDesktop
                        compact
                        model={roomModel}
                        selectedRange={selectedRange}
                        onSelectionCommit={(range) => setSelectedRange(range)}
                        onSelectionClear={() => setSelectedRange(null)}
                      />
                    ) : (
                      <ScheduleListMobile
                        model={roomModel}
                        selectedRange={selectedRange}
                        onSelectionCommit={(range) => setSelectedRange(range)}
                        onSelectionClear={() => setSelectedRange(null)}
                      />
                    )}
                  </div>
                </motion.div>

                {/* Booking Action Box */}
                <motion.div variants={{hidden: {opacity: 0, y: 10}, visible: {opacity: 1, y: 0}}}>
                  <div className="flex flex-col gap-4 rounded-[2rem] border border-white/10 bg-[linear-gradient(145deg,hsl(var(--surface-glass-1)),hsl(var(--surface-glass-2)))] p-4 sm:flex-row sm:items-center sm:justify-between sm:p-5 shadow-lg backdrop-blur-xl">
                    <div className="min-w-0">
                      <p className="m-0 text-xs font-medium uppercase tracking-wider text-muted-foreground">
                        {t('schedule.legend.selection')}
                      </p>
                      <p className="rf-tabular m-0 mt-1 text-lg font-bold text-foreground">
                        {selectedRange ? `${selectedRange.start} — ${selectedRange.end}` : <span className="opacity-50">{t('roomDetail.selectRange')}</span>}
                      </p>
                    </div>
                    <div className="flex w-full items-center gap-2 sm:w-auto">
                      <button
                        type="button"
                        onClick={() => setSelectedRange(null)}
                        data-cursor={!selectedRange || createMutation.isPending ? 'locked' : 'view'}
                        data-cursor-text={!selectedRange || createMutation.isPending ? undefined : t('cursor.clear')}
                        disabled={!selectedRange || createMutation.isPending}
                        className="w-1/2 rounded-2xl border border-white/20 bg-white/5 px-4 py-3 text-sm font-semibold text-foreground transition-colors hover:bg-white/10 disabled:cursor-not-allowed disabled:opacity-40 sm:w-auto"
                      >
                        {t('common.reset')}
                      </button>
                      <MagneticButton
                        onClick={() => void handleBook()}
                        data-cursor={!selectedRange || createMutation.isPending || isHolidaySelected ? 'locked' : 'book'}
                        data-cursor-text={!selectedRange || createMutation.isPending || isHolidaySelected ? undefined : t('cursor.book')}
                        disabled={!selectedRange || createMutation.isPending || isHolidaySelected || holidaysQuery.isPending || holidaysQuery.isError}
                        className="w-1/2 rounded-2xl border border-primary/50 bg-primary/80 px-6 py-3 text-sm font-bold text-primary-foreground shadow-[0_0_20px_hsl(var(--primary)/0.4)] transition-all hover:bg-primary hover:shadow-[0_0_30px_hsl(var(--primary)/0.6)] disabled:cursor-not-allowed disabled:opacity-40 disabled:shadow-none sm:w-auto"
                      >
                        {createMutation.isPending ? t('roomDetail.bookingPending') : t('roomDetail.book')}
                      </MagneticButton>
                    </div>
                  </div>
                </motion.div>
                
              </motion.div>
            )}
          </motion.div>
        </div>
      </motion.div>
    </motion.div>
    </>
  );
};

export default RoomDetailOverlay;
