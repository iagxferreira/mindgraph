use async_trait::async_trait;

use crate::app::AppState;

#[async_trait]
pub trait Plugin: Send + Sync {
    fn name(&self) -> &str;

    async fn on_load(&self, _state: &mut AppState) -> Result<(), Box<dyn std::error::Error>> {
        Ok(())
    }
}
