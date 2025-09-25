<template>
  <v-container class="py-10">
    <v-row justify="center">
      <v-col cols="12" md="8">
        <v-card class="pa-6">
          <div class="d-flex align-center ga-4">
            <v-avatar size="64" class="bg-pink-lighten-4"><v-icon color="pink">mdi-account</v-icon></v-avatar>
            <div>
              <div class="text-subtitle-1">{{ user?.nickName || 'Guest' }}</div>
              <div class="text-caption">{{ user?.email }}</div>
            </div>
            <v-spacer/>
            <v-btn color="pink" variant="tonal" @click="logout" to="/">로그아웃</v-btn>
          </div>
          <v-divider class="my-4"/>

          <v-tabs v-model="tab" color="pink" align-tabs="center">
            <v-tab value="following">팔로잉 ({{ followingList.length }})</v-tab>
            <v-tab value="followers">팔로워 ({{ followerList.length }})</v-tab>
          </v-tabs>

          <v-window v-model="tab">
            <v-window-item value="following">
              <v-list>
                <v-list-item v-for="f in followingList" :key="f.userPid">
                  <v-list-item-title>{{ f.nickName }}</v-list-item-title>
                  <v-list-item-subtitle>{{ f.email }}</v-list-item-subtitle>
                  <template v-slot:append>
                    <v-btn icon variant="text" color="red" size="small" @click="unfollow(f.userPid)">
                      <v-icon>mdi-account-remove</v-icon>
                    </v-btn>
                  </template>
                </v-list-item>
                <v-list-item v-if="followingList.length === 0">
                  <v-list-item-title>팔로잉하는 사용자가 없습니다.</v-list-item-title>
                </v-list-item>
              </v-list>
            </v-window-item>

            <v-window-item value="followers">
              <v-list>
                <v-list-item v-for="f in followerList" :key="f.userPid">
                  <v-list-item-title>{{ f.nickName }}</v-list-item-title>
                  <v-list-item-subtitle>{{ f.email }}</v-list-item-subtitle>
                  <!-- 팔로워 목록에서는 수락/거절 또는 차단 등의 액션이 필요할 수 있음 -->
                </v-list-item>
                <v-list-item v-if="followerList.length === 0">
                  <v-list-item-title>팔로워가 없습니다.</v-list-item-title>
                </v-list-item>
              </v-list>
            </v-window-item>
          </v-window>
        </v-card>
      </v-col>
    </v-row>
  </v-container>
</template>

<script setup>
import { ref, onMounted, watch } from 'vue'
import { useAuthStore } from '../stores/auth'
import { storeToRefs } from 'pinia'
import { useRouter } from 'vue-router'
import followService from '../services/follow' // Import the new service

const store = useAuthStore()
const { user } = storeToRefs(store)
const router = useRouter()

const tab = ref(null) // For v-tabs
const followingList = ref([])
const followerList = ref([])

async function fetchFollowLists() {
  if (user.value?.userPid) { // Use userPid from the backend User entity
    try {
      const [followingRes, followerRes] = await Promise.all([
        followService.getFollowingList(user.value.userPid),
        followService.getFollowerList(user.value.userPid)
      ])
      followingList.value = followingRes.data
      followerList.value = followerRes.data
    } catch (error) {
      console.error('Failed to fetch follow lists:', error)
      // Optionally, show an alert or message to the user
    }
  }
}

async function unfollow(targetUserPid) {
  // In a real app, you'd need the followId to unfollow.
  // For simplicity, we'll assume we can unfollow by targetUserPid for now,
  // but the backend deleteFollow expects followId.
  // This part needs refinement based on how followId is obtained.
  // For now, let's just remove from UI and log a message.
  alert('언팔로우 기능은 followId를 기반으로 백엔드와 연동되어야 합니다. 현재는 UI에서만 제거됩니다.')
  followingList.value = followingList.value.filter(f => f.userPid !== targetUserPid)
  // A more complete implementation would involve:
  // 1. Fetching the specific followId for the follower-followee pair.
  // 2. Calling followService.unfollow(followId).
  // 3. Re-fetching the lists or updating the local state based on success.
}


function logout(){
  store.logout()
  router.replace({ name: 'home' })
}

onMounted(() => {
  fetchFollowLists()
})

// Watch for user changes (e.g., after login) to refetch lists
watch(user, (newUser) => {
  if (newUser) {
    fetchFollowLists();
  } else {
    followingList.value = [];
    followerList.value = [];
  }
}, { immediate: true }); // immediate: true to run on initial load if user is already there
</script>