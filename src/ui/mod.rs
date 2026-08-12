mod app;
mod widgets;

use ratatui::prelude::Frame;

use crate::app::AppState;

pub fn draw(frame: &mut Frame<'_>, app: &AppState) {
    app::draw(frame, app);
}
