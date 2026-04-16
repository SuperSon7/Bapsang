import apiClient from "/src/core/api/api.js";

export async function like(postId){
    try {
        const response = await apiClient.put(`/posts/${postId}/likes`)
        return response;
    } catch (error) {
        console.error('좋아요 요청 실패', error);
        throw error;
    }
}

export async function unlike(postId){
    try {
        const response = await apiClient.delete(`/posts/${postId}/likes`)
        return response;
    } catch (error) {
        console.error('좋아요 취소 요청 실패', error);
        throw error;
    }
}
