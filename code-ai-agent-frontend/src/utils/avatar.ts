export const DEFAULT_USER_AVATAR = '/userAvatar.svg'

export const getAvatarUrl = (avatar?: string | null) => {
  const trimmedAvatar = avatar?.trim()
  return trimmedAvatar || DEFAULT_USER_AVATAR
}
